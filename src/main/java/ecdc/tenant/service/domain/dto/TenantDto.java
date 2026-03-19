package ecdc.tenant.service.domain.dto;

import ecdc.tenant.service.domain.model.Tenant;
import ecdc.tenant.service.enums.TenantLifecycleStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantDto {
    private UUID id;

    @NotBlank
    @Size(max = 64)
    private String code;

    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 1000)
    private String description;

    @NotBlank
    @Size(max = 128)
    private String tenantKey;

    @NotBlank
    @Size(max = 255)
    private String participantDid;

    @NotBlank
    @Size(max = 64)
    private String participantBpn;

    @NotBlank
    @Size(max = 255)
    private String contactName;

    @NotBlank
    @Email
    @Size(max = 320)
    private String contactEmail;

    @Size(max = 32)
    @Pattern(regexp = "^[+0-9()\\-\\s]*$", message = "contactPhone must contain only phone-safe characters")
    private String contactPhone;

    @NotBlank
    @Size(max = 255)
    private String k8sNamespace;

    @NotBlank
    @Size(max = 128)
    private String databaseName;

    @Size(max = 128)
    private String databaseSchema;

    @NotBlank
    @Size(max = 128)
    private String databaseUsername;

    @NotBlank
    @Size(max = 512)
    private String managementApiBaseUrl;

    @NotBlank
    @Size(max = 512)
    private String publicApiBaseUrl;

    @Size(max = 255)
    private String controlPlaneService;

    @Size(max = 255)
    private String dataPlaneService;

    @Builder.Default
    private TenantLifecycleStatus lifecycleStatus = TenantLifecycleStatus.PROVISIONING;

    @Size(max = 2000)
    private String lastError;

    @Builder.Default
    private Boolean enabled = true;

    public Tenant toEntity(Tenant tenant) {
        tenant.setCode(this.code);
        tenant.setName(this.name);
        tenant.setDescription(this.description);
        tenant.setTenantKey(this.tenantKey);
        tenant.setParticipantDid(this.participantDid);
        tenant.setParticipantBpn(this.participantBpn);
        tenant.setContactName(this.contactName);
        tenant.setContactEmail(this.contactEmail);
        tenant.setContactPhone(this.contactPhone);
        tenant.setK8sNamespace(this.k8sNamespace);
        tenant.setDatabaseName(this.databaseName);
        tenant.setDatabaseSchema(this.databaseSchema);
        tenant.setDatabaseUsername(this.databaseUsername);
        tenant.setManagementApiBaseUrl(this.managementApiBaseUrl);
        tenant.setPublicApiBaseUrl(this.publicApiBaseUrl);
        tenant.setControlPlaneService(this.controlPlaneService);
        tenant.setDataPlaneService(this.dataPlaneService);
        tenant.setLifecycleStatus(this.lifecycleStatus == null ? TenantLifecycleStatus.PROVISIONING : this.lifecycleStatus);
        tenant.setLastError(this.lastError);
        tenant.setEnabled(this.enabled == null || this.enabled);
        return tenant;
    }
}
