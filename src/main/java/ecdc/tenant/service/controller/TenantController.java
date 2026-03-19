package ecdc.tenant.service.controller;

import ecdc.tenant.service.domain.dto.TenantDto;
import ecdc.tenant.service.record.Response;
import ecdc.tenant.service.service.TenantService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

import static ecdc.tenant.service.utility.AppConstants.TENANT_CONTEXT_PATH;

@RestController
@RequestMapping(TENANT_CONTEXT_PATH)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Tenant Endpoints", description = "This contains all endpoints that are used to interact with the tenant model")
public class TenantController {

    private final TenantService tenantService;

    /**
     * Handles a GET request to retrieve all tenants.
     *
     * @param params The parameters for the list of tenants to retrieve like pagination,filters etc.
     * @return ResponseEntity containing the Response with the list of tenants per params.
     */
    @GetMapping
    Response findTenants(@RequestParam Map<String, String> params) {
        return tenantService.findAll(params).getBody();
    }

    /**
     * Handles a GET request to retrieve a tenant by its ID.
     *
     * @param id The ID of the tenant to retrieve.
     * @return ResponseEntity containing the Response with the requested tenant.
     */
    @GetMapping("/{id}")
    public Response findTenantById(@PathVariable UUID id){
        return tenantService.findById(id).getBody();
    }

    /**
     * Handles a POST request to create a new tenant.
     *
     * @param dto The TenantDto containing information of the new tenant.
     * @return ResponseEntity containing the Response with the created tenant.
     */
    @PostMapping
    public Response createTenant(@Valid @RequestBody TenantDto dto){
        return tenantService.create(dto).getBody();
    }

    /**
     * Handles a PUT request to update an existing tenant.
     *
     * @param dto The updated TenantDto containing information to update tenant.
     * @param id The ID of the tenant to be updated.
     * @param enabled The enabled state of the tenant to be updated.
     * @return ResponseEntity containing the Response with the updated tenant.
     */
    @PutMapping("/{id}")
    public Response updateTenant(@Valid @RequestBody TenantDto dto, @PathVariable(name = "id") UUID id,
                                          @RequestParam (name = "enabled", defaultValue = "true") boolean enabled){
        dto.setEnabled(enabled);
        return tenantService.update(dto,id).getBody();
    }
}
