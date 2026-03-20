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

import java.util.HashMap;
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

    @Value("${tenant.provisioning.helm.chart:}")
    private String helmChart;

    @Value("${tenant.provisioning.helm.base-values:}")
    private String helmBaseValues;

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

            Map<String, Object> overrides = buildTenantOverrides(tenant, dbPassword);
            triggerHelm(tenant, overrides);

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

