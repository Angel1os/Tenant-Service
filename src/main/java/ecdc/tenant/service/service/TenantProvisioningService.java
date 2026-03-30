package ecdc.tenant.service.service;

import ecdc.tenant.service.domain.model.Tenant;
import ecdc.tenant.service.enums.TenantLifecycleStatus;
import ecdc.tenant.service.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ecdc.tenant.service.utility.AppUtils.getRootCauseMessage;

/**
 * Provisions a per-tenant Tractus-X / EDC connector instance.
 *
 * Implementation notes:
 * - Base connector props live in the Helm chart (server-side templates).
 * - This service generates tenant-specific Helm override values and triggers Helm.
 * - DB passwords / secrets should ideally be stored as Kubernetes Secrets.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantProvisioningService {

    private final TenantRepository tenantRepository;

    @Value("${tenant.provisioning.enabled:false}")
    private boolean provisioningEnabled;

    /**
     * Provisioning mode:
     * - helm: use Helm chart deployments (Kubernetes)
     * - docker-compose: generate a compose project per tenant (local/dev)
     */
    @Value("${tenant.provisioning.mode:docker-compose}")
    private String provisioningMode;

    @Value("${tenant.provisioning.helm.chart:}")
    private String helmChart;

    @Value("${tenant.provisioning.helm.base-values:}")
    private String helmBaseValues;

    @Value("${tenant.provisioning.root-dir:./tenant-runtimes}")
    private String tenantRootDir;

    @Value("${tenant.provisioning.compose.controlplane.image:edc-runtime-memory:latest}")
    private String controlPlaneImage;

    @Value("${tenant.provisioning.compose.dataplane.image:edc-dataplane-hashicorp-vault:latest}")
    private String dataPlaneImage;

    /**
     * When set, joins the generated compose project to an existing external network
     * that already contains shared infra (postgres/redis/elasticsearch/keycloak).
     * Example: docker-services_default (from your tractus-x docker-compose project)
     */
    @Value("${tenant.provisioning.compose.external-network:}")
    private String composeExternalNetwork;

    /**
     * Base config template that holds platform-standard properties.
     * This file can be replaced by platform engineers without changing code.
     */
    @Value("${tenant.provisioning.base-config-template:classpath:provisioning/base-config.docker.properties}")
    private String baseConfigTemplate;

    @Value("${tenant.platform.postgres.host:localhost}")
    private String postgresHost;

    @Value("${tenant.platform.postgres.port:5432}")
    private int postgresPort;


    /**
     * Non-blocking provisioning entrypoint.
     * The API thread returns immediately while the actual provisioning runs in background.
     */
    @Async("tenantProvisioningExecutor")
    public void provisionAsync(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(
                () -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Tenant not found")
        );
        provision(tenant);
    }

    /**
     * Triggers provisioning for the given tenant.
     * This method updates tenant lifecycle status in DB.
     */
    public void provision(Tenant tenant) {
        log.info("Provisioning tenantKey={} in namespace={}", tenant.getTenantKey(), tenant.getK8sNamespace());

        if (!provisioningEnabled) {
            tenant.setLifecycleStatus(TenantLifecycleStatus.FAILED);
            tenant.setLastError("Provisioning disabled (tenant.provisioning.enabled=false)");
            tenantRepository.save(tenant);
            return;
        }

        try {
            // Validate required runtime metadata
            if (tenant.getK8sNamespace() == null || tenant.getK8sNamespace().isBlank()) {
                throw new IllegalArgumentException("k8sNamespace is required");
            }
            if (tenant.getDatabaseName() == null || tenant.getDatabaseName().isBlank()) {
                throw new IllegalArgumentException("databaseName is required");
            }

            String dbPassword = "dev-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

            if ("docker-compose".equalsIgnoreCase(provisioningMode)) {
                provisionWithDockerCompose(tenant, dbPassword);
            } else if ("helm".equalsIgnoreCase(provisioningMode)) {
                Map<String, Object> overrides = buildTenantOverrides(tenant, dbPassword);
                triggerHelm(tenant, overrides);
            } else {
                throw new IllegalStateException("Unsupported tenant.provisioning.mode=" + provisioningMode);
            }

            boolean ready = true;

            tenant.setLifecycleStatus(ready ? TenantLifecycleStatus.READY : TenantLifecycleStatus.FAILED);
            tenant.setLastError(ready ? null : "Connector pods not ready");
            tenantRepository.save(tenant);

        } catch (Exception ex) {
            String root = getRootCauseMessage(ex);
            log.error("Provisioning failed tenantKey={}: {}", tenant.getTenantKey(), root, ex);

            tenant.setLifecycleStatus(TenantLifecycleStatus.FAILED);
            tenant.setLastError(root);
            tenantRepository.save(tenant);

            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Provisioning failed"
            );
        }
    }

    private void provisionWithDockerCompose(Tenant tenant, String dbPassword) throws Exception {
        // Allocate deterministic per-tenant ports to allow multiple tenants locally.
        int offset = Math.abs(tenant.getTenantKey().hashCode() % 100) * 10; // 0..990
        int webHttpPort = 28080 + offset;
        int protocolPort = 28081 + offset;
        int controlPort = 28082 + offset;

        // Update URLs to local ports for dev validation
        tenant.setPublicApiBaseUrl("http://localhost:" + webHttpPort + "/api");
        tenant.setManagementApiBaseUrl("http://localhost:" + protocolPort + "/protocol");
        tenantRepository.save(tenant);

        Path tenantDir = Path.of(tenantRootDir, tenant.getTenantKey());
        Path configDir = tenantDir.resolve("config");
        Files.createDirectories(configDir);

        // 1) Generate config.properties (base + tenant overrides)
        String base = readTemplate(baseConfigTemplate);
        String tenantProps = buildTenantProperties(tenant, dbPassword, webHttpPort, protocolPort, controlPort);
        String merged = (base == null ? "" : base.trim() + "\n\n") + tenantProps;

        Path configFile = configDir.resolve("config.properties");
        Files.writeString(
                configFile,
                merged + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );

        // 2) Generate docker-compose.yml for CP/DP only (shared infra assumed)
        String composeYaml = buildComposeYaml(tenant, configFile);
        Path composeFile = tenantDir.resolve("docker-compose.yml");
        Files.writeString(
                composeFile,
                composeYaml + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );

        // 3) docker compose up -d
        String projectName = "tenant_" + tenant.getTenantKey().replace("-", "_");
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("compose");
        cmd.add("-p");
        cmd.add(projectName);
        cmd.add("-f");
        cmd.add(composeFile.toAbsolutePath().toString());
        cmd.add("up");
        cmd.add("-d");

        log.info("Executing docker compose: {}", String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        Process p = pb.start();
        int code = p.waitFor();
        if (code != 0) {
            throw new IllegalStateException("docker compose exited with code " + code);
        }
    }

    private String buildTenantProperties(Tenant tenant, String dbPassword, int webHttpPort, int protocolPort, int controlPort) {
        String dbUrl = "jdbc:postgresql://" + postgresHost + ":" + postgresPort + "/" + tenant.getDatabaseName();

        return ""
                + "# ---------- Runtime ports ----------\n"
                + "web.http.port=" + webHttpPort + "\n"
                + "web.http.path=/api\n"
                + "web.http.protocol.port=" + protocolPort + "\n"
                + "web.http.protocol.path=/protocol\n"
                + "web.http.control.port=" + controlPort + "\n"
                + "web.http.control.path=/control\n\n"
                + "# ---------- Connector identity ----------\n"
                + "edc.connector.name=Tractus-X Connector (" + tenant.getTenantKey() + ")\n"
                + "tractusx.edc.participant.bpn=" + tenant.getParticipantBpn() + "\n"
                + "edc.iam.issuer.id=" + tenant.getParticipantDid() + "\n"
                + "edc.iam.trusted-issuer.id=" + tenant.getParticipantDid() + "\n"
                + "edc.iam.trusted-issuer.id.id=" + tenant.getParticipantDid() + "\n\n"
                + "# ---------- DSP callback ----------\n"
                + "edc.dsp.callback.address=http://localhost:" + protocolPort + "/protocol\n\n"
                + "# ---------- DB ----------\n"
                + "edc.datasource.default.url=" + dbUrl + "\n"
                + "edc.datasource.default.user=" + tenant.getDatabaseUsername() + "\n"
                + "edc.datasource.default.password=" + dbPassword + "\n"
                + "edc.datasource.default.port=" + postgresPort + "\n\n"
                + "# ---------- Service exchange ----------\n"
                + "edc.serviceexchange.provider.public.url=http://localhost:" + webHttpPort + "/api\n";
    }

    private String buildComposeYaml(Tenant tenant, Path configFile) {
        String cpName = "edc_controlplane_" + tenant.getTenantKey().replace("-", "_");
        String dpName = "edc_dataplane_" + tenant.getTenantKey().replace("-", "_");

        StringBuilder sb = new StringBuilder();
        sb.append("services:\n");
        sb.append("  controlplane:\n");
        sb.append("    image: ").append(controlPlaneImage).append("\n");
        sb.append("    container_name: ").append(cpName).append("\n");
        sb.append("    restart: unless-stopped\n");
        sb.append("    volumes:\n");
        sb.append("      - ").append(toComposePath(configFile)).append(":/app/configuration.properties:ro\n");
        sb.append("    environment:\n");
        sb.append("      OTEL_TRACES_EXPORTER: none\n");
        sb.append("      OTEL_LOGS_EXPORTER: none\n");
        sb.append("      OTEL_METRICS_EXPORTER: none\n\n");

        sb.append("  dataplane:\n");
        sb.append("    image: ").append(dataPlaneImage).append("\n");
        sb.append("    container_name: ").append(dpName).append("\n");
        sb.append("    restart: unless-stopped\n");
        sb.append("    depends_on:\n");
        sb.append("      - controlplane\n");
        sb.append("    volumes:\n");
        sb.append("      - ").append(toComposePath(configFile)).append(":/app/configuration.properties:ro\n");
        sb.append("    environment:\n");
        sb.append("      OTEL_TRACES_EXPORTER: none\n");
        sb.append("      OTEL_LOGS_EXPORTER: none\n");
        sb.append("      OTEL_METRICS_EXPORTER: none\n\n");

        if (composeExternalNetwork != null && !composeExternalNetwork.isBlank()) {
            sb.append("networks:\n");
            sb.append("  default:\n");
            sb.append("    external: true\n");
            sb.append("    name: ").append(composeExternalNetwork).append("\n");
        }

        return sb.toString();
    }

    private String toComposePath(Path path) {
        // Docker Compose on Windows expects Windows paths; keep as-is
        return path.toAbsolutePath().toString();
    }

    private String readTemplate(String location) {
        try {
            if (location == null || location.isBlank()) {
                return "";
            }
            if (location.startsWith("classpath:")) {
                String cp = location.substring("classpath:".length());
                try (var in = TenantProvisioningService.class.getClassLoader().getResourceAsStream(cp.startsWith("/") ? cp.substring(1) : cp)) {
                    if (in == null) {
                        return "";
                    }
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            return Files.readString(Path.of(location), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to read base config template '{}': {}", location, e.getMessage());
            return "";
        }
    }

    private Map<String, Object> buildTenantOverrides(Tenant tenant, String dbPassword) {
        Map<String, Object> values = new HashMap<>();

        // --- Identity (tenant-provided) ---
        values.put("tractusx.edc.participant.bpn", tenant.getParticipantBpn());
        values.put("edc.iam.issuer.id", tenant.getParticipantDid());
        values.put("edc.iam.trusted-issuer.id", tenant.getParticipantDid());

        // --- Runtime/callback wiring (tenant-generated) ---
        values.put("edc.serviceexchange.provider.public.url", tenant.getPublicApiBaseUrl());
        values.put("edc.dsp.callback.address", tenant.getManagementApiBaseUrl());


        //TODO password should be secret-based
        String dbUrl = "jdbc:postgresql://" + postgresHost + ":" + postgresPort + "/" + tenant.getDatabaseName();
        values.put("edc.datasource.default.url", dbUrl);
        values.put("edc.datasource.default.user", tenant.getDatabaseUsername());
        values.put("edc.datasource.default.password", dbPassword);
        values.put("k8s.namespace", tenant.getK8sNamespace());
        values.put("edc.controlplane.serviceName", tenant.getControlPlaneService());
        values.put("edc.dataplane.serviceName", tenant.getDataPlaneService());

        return values;
    }

    private void triggerHelm(Tenant tenant, Map<String, Object> overrides) {
        if (helmChart == null || helmChart.isBlank()) {
            throw new IllegalStateException("Missing tenant.provisioning.helm.chart property");
        }

        // This simplistic implementation uses --set for overrides.
        // For large/complex configs, consider generating a tenant-specific values.yaml file.
        String releaseName = tenant.getTenantKey();
        String namespace = tenant.getK8sNamespace();

        // Build: helm upgrade --install <release> <chart> -n <namespace> -f <baseValues> --set key=value ...
        try {
            java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add("helm");
            cmd.add("upgrade");
            cmd.add("--install");
            cmd.add(releaseName);
            cmd.add(helmChart);
            cmd.add("-n");
            cmd.add(namespace);

            if (helmBaseValues != null && !helmBaseValues.isBlank()) {
                cmd.add("-f");
                cmd.add(helmBaseValues);
            }

            for (Map.Entry<String, Object> e : overrides.entrySet()) {
                String key = e.getKey();
                Object val = e.getValue();
                cmd.add("--set");
                cmd.add(key + "=" + String.valueOf(val));
            }

            log.info("Executing helm command: {}", String.join(" ", cmd));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.inheritIO();
            Process process = pb.start();
            int code = process.waitFor();
            if (code != 0) {
                throw new IllegalStateException("helm exited with code " + code);
            }
        } catch (Exception ex) {
            throw new RuntimeException("Failed to execute helm: " + ex.getMessage(), ex);
        }
    }
}

