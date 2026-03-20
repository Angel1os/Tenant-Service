package ecdc.tenant.service.domain.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateTenantRequest {

    @NotBlank
    @Size(max = 64)
    private String name;
    @Size(max = 1000)
    private String description;
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
    private String contactPhone;
}
