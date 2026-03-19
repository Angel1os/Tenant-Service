package ecdc.tenant.service.domain.model;

import ecdc.tenant.service.domain.dto.TenantDto;
import ecdc.tenant.service.enums.TenantLifecycleStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "tenant",
        indexes = {
                @Index(name = "idx_tenant_code", columnList = "code"),
                @Index(name = "idx_tenant_enabled", columnList = "enabled")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_tenant_code", columnNames = {"code"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "tenant_key", nullable = false, length = 128)
    private String tenantKey;

    @Column(name = "participant_did", nullable = false, length = 255)
    private String participantDid;

    @Column(name = "participant_bpn", nullable = false, length = 64)
    private String participantBpn;

    @Column(name = "contact_name", nullable = false, length = 255)
    private String contactName;

    @Column(name = "contact_email", nullable = false, length = 320)
    private String contactEmail;

    @Column(name = "contact_phone", length = 32)
    private String contactPhone;

    @Column(name = "k8s_namespace", nullable = false, length = 255)
    private String k8sNamespace;

    @Column(name = "database_name", nullable = false, length = 128)
    private String databaseName;

    @Column(name = "database_schema", length = 128)
    private String databaseSchema;

    @Column(name = "database_username", nullable = false, length = 128)
    private String databaseUsername;

    @Column(name = "management_api_base_url", nullable = false, length = 512)
    private String managementApiBaseUrl;

    @Column(name = "public_api_base_url", nullable = false, length = 512)
    private String publicApiBaseUrl;

    @Column(name = "control_plane_service", length = 255)
    private String controlPlaneService;

    @Column(name = "data_plane_service", length = 255)
    private String dataPlaneService;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 32)
    @Builder.Default
    private TenantLifecycleStatus lifecycleStatus = TenantLifecycleStatus.PROVISIONING;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public TenantDto toDto() {
        return TenantDto.builder()
                .id(id)
                .code(code)
                .name(name)
                .description(description)
                .tenantKey(tenantKey)
                .participantDid(participantDid)
                .participantBpn(participantBpn)
                .contactName(contactName)
                .contactEmail(contactEmail)
                .contactPhone(contactPhone)
                .k8sNamespace(k8sNamespace)
                .databaseName(databaseName)
                .databaseSchema(databaseSchema)
                .databaseUsername(databaseUsername)
                .managementApiBaseUrl(managementApiBaseUrl)
                .publicApiBaseUrl(publicApiBaseUrl)
                .controlPlaneService(controlPlaneService)
                .dataPlaneService(dataPlaneService)
                .lifecycleStatus(lifecycleStatus)
                .lastError(lastError)
                .enabled(enabled)
                .build();
    }
}
