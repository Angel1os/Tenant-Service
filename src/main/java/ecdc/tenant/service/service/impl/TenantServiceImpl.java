package ecdc.tenant.service.service.impl;

import ecdc.tenant.service.domain.dto.TenantDto;
import ecdc.tenant.service.domain.request.CreateTenantRequest;
import ecdc.tenant.service.domain.model.Tenant;
import ecdc.tenant.service.enums.StatusCode;
import ecdc.tenant.service.enums.TenantLifecycleStatus;
import ecdc.tenant.service.record.Response;
import ecdc.tenant.service.repository.TenantRepository;
import ecdc.tenant.service.service.TenantService;
import ecdc.tenant.service.service.TenantProvisioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static ecdc.tenant.service.utility.AppConstants.*;
import static ecdc.tenant.service.utility.AppUtils.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantServiceImpl implements TenantService {

    private final TenantRepository tenantRepository;
    private final TenantProvisioningService tenantProvisioningService;

    @Override
    public ResponseEntity<Response> create(CreateTenantRequest request) {
        log.info("inside create tenant :: participantBpn={}, participantDid={}",
                request.getParticipantBpn(), request.getParticipantDid());

        validateUniqueForCreate(request, null);

        String generatedCode = generateTenantCode();
        String tenantKey = toTenantKey(generatedCode);

        /**
         * Deriving tenant runtime config
         */
        String k8sNamespace = "edc-" + tenantKey;
        String databaseName = truncate("edc_" + tenantKey, 128);
        String databaseUsername = truncate("edc_" + tenantKey + "_user", 128);

        String tenantBaseUrl = defaultIfBlank(System.getenv("TENANT_GATEWAY_BASE_URL"), "https://ecdc.docexploit.com/tenant")
                + "/" + tenantKey;

        String publicApiBaseUrl = tenantBaseUrl + "/api";
        String managementApiBaseUrl = tenantBaseUrl + "/protocol";

        String controlPlaneService = "edc-" + tenantKey + "-cp";
        String dataPlaneService = "edc-" + tenantKey + "-dp";

        Tenant tenant = Tenant.builder()
                .code(generatedCode)
                .tenantKey(tenantKey)
                .name(request.getName())
                .description(request.getDescription())
                .participantDid(request.getParticipantDid())
                .participantBpn(request.getParticipantBpn())
                .contactName(request.getContactName())
                .contactEmail(request.getContactEmail())
                .contactPhone(request.getContactPhone())
                .enabled(true)
                .k8sNamespace(k8sNamespace)
                .databaseName(databaseName)
                .databaseSchema(null)
                .databaseUsername(databaseUsername)
                .managementApiBaseUrl(managementApiBaseUrl)
                .publicApiBaseUrl(publicApiBaseUrl)
                .controlPlaneService(controlPlaneService)
                .dataPlaneService(dataPlaneService)
                .lifecycleStatus(TenantLifecycleStatus.PROVISIONING)
                .lastError(null)
                .build();

        Tenant savedTenant = tenantRepository.save(tenant);

        // Non-blocking provisioning: trigger background worker and return immediately.
        tenantProvisioningService.provisionAsync(savedTenant.getId());

        return getResponse(
                "Provisioning started",
                HttpStatus.ACCEPTED,
                StatusCode.SUCCESS,
                savedTenant.toDto()
        ).toResponseEntity();
    }

    @Override
    public ResponseEntity<Response> update(TenantDto tenantDto, UUID id) {
        log.info("inside update tenant with id :: {} and payload :: {}", id, tenantDto);
        Tenant existingTenant = tenantRepository.findById(id).orElse(null);

        if (isNotNullOrEmpty(existingTenant)) {
            // Only update client-editable fields to avoid wiping deployer-generated runtime config
            if (isNotNullOrEmpty(tenantDto.getName())) {
                existingTenant.setName(tenantDto.getName());
            }
            existingTenant.setDescription(tenantDto.getDescription());
            if (isNotNullOrEmpty(tenantDto.getContactName())) {
                existingTenant.setContactName(tenantDto.getContactName());
            }
            if (isNotNullOrEmpty(tenantDto.getContactEmail())) {
                existingTenant.setContactEmail(tenantDto.getContactEmail());
            }
            existingTenant.setContactPhone(tenantDto.getContactPhone());
            existingTenant.setEnabled(tenantDto.getEnabled() == null || tenantDto.getEnabled());

            // If participant identity fields are being changed, validate uniqueness.
            if (tenantDto.getParticipantDid() != null && !tenantDto.getParticipantDid().equals(existingTenant.getParticipantDid())) {
                validateUniqueForCreate(requestFromExisting(tenantDto, existingTenant), id);
            }
            if (tenantDto.getParticipantBpn() != null && !tenantDto.getParticipantBpn().equals(existingTenant.getParticipantBpn())) {
                validateUniqueForCreate(requestFromExisting(tenantDto, existingTenant), id);
            }

            Tenant updatedTenant = tenantRepository.save(existingTenant);
            return getResponse(UPDATED_MESSAGE, HttpStatus.ACCEPTED, StatusCode.SUCCESS, updatedTenant.toDto()).toResponseEntity();
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, NO_RECORD_FOUND);
    }

    @Override
    public ResponseEntity<Response> findAll(Map<String, String> queryParams) {
        log.info("inside findAll tenants with given params :: {}", queryParams);
        boolean paginate = Boolean.parseBoolean(queryParams.getOrDefault(PARAM_PAGINATE, DEFAULT_PAGINATE));
        String searchValue = queryParams.getOrDefault(PARAM_SEARCH, DEFAULT_SEARCH).trim().toLowerCase();

        if (paginate) {
            Pageable pageable = getPageRequest(queryParams);
            Page<Tenant> tenantPage = tenantRepository.findAll(pageable);
            if (isNotNullOrEmpty(searchValue)) {
                List<Tenant> filtered = tenantPage.stream().filter(tenant ->
                        tenant.getCode().toLowerCase().contains(searchValue)
                                || tenant.getName().toLowerCase().contains(searchValue)
                                || tenant.getTenantKey().toLowerCase().contains(searchValue)
                                || tenant.getParticipantDid().toLowerCase().contains(searchValue)
                                || tenant.getParticipantBpn().toLowerCase().contains(searchValue)
                ).toList();
                return buildListResponse(filtered, Tenant::toDto).toResponseEntity();
            }
            return buildPageResponse(tenantPage, Tenant::toDto).toResponseEntity();
        }

        List<Tenant> tenants = tenantRepository.findAll();
        if (isNotNullOrEmpty(searchValue)) {
            tenants = tenants.stream().filter(tenant ->
                    tenant.getCode().toLowerCase().contains(searchValue)
                            || tenant.getName().toLowerCase().contains(searchValue)
                            || tenant.getTenantKey().toLowerCase().contains(searchValue)
                            || tenant.getParticipantDid().toLowerCase().contains(searchValue)
                            || tenant.getParticipantBpn().toLowerCase().contains(searchValue)
            ).toList();
        }
        return buildListResponse(tenants, Tenant::toDto).toResponseEntity();
    }

    @Override
    public ResponseEntity<Response> findById(UUID id) {
        log.info("inside find tenant by id :: {}", id);
        Optional<Tenant> optionalTenant = tenantRepository.findById(id);
        if (optionalTenant.isPresent()) {
            TenantDto existingTenant = optionalTenant.get().toDto();
            return getResponse(FETCH_SUCCESS_MESSAGE_BY_ID + " " + id,
                    HttpStatus.OK, StatusCode.SUCCESS, existingTenant).toResponseEntity();
        }
        return getResponse(NO_RECORD_FOUND, HttpStatus.NOT_FOUND, StatusCode.CLIENT_ERROR).toResponseEntity();
    }

    private void validateUniqueForCreate(CreateTenantRequest dto, UUID currentTenantId) {
        assertUnique(
                tenantRepository.findByParticipantDid(dto.getParticipantDid()),
                currentTenantId,
                "Participant DID already exists"
        );
        assertUnique(
                tenantRepository.findByParticipantBpn(dto.getParticipantBpn()),
                currentTenantId,
                "Participant BPN already exists"
        );
    }

    private void assertUnique(Optional<Tenant> existing, UUID currentTenantId, String errorMessage) {
        if (existing.isPresent() && (currentTenantId == null || !existing.get().getId().equals(currentTenantId))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
        }
    }

    private String generateTenantCode() {
        // Keep within Tenant.code max length (64)
        return "TNT-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String toTenantKey(String code) {
        return code.toLowerCase().replaceAll("[^a-z0-9\\-]", "-");
    }

    private String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }

    private String defaultIfBlank(String value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return value.trim().isEmpty() ? defaultValue : value;
    }

    private CreateTenantRequest requestFromExisting(TenantDto dto, Tenant existing) {
        // Build a minimal CreateTenantRequest from update payload + existing values
        CreateTenantRequest r = new CreateTenantRequest();
        r.setParticipantDid(dto.getParticipantDid() != null ? dto.getParticipantDid() : existing.getParticipantDid());
        r.setParticipantBpn(dto.getParticipantBpn() != null ? dto.getParticipantBpn() : existing.getParticipantBpn());
        r.setName(existing.getName());
        r.setDescription(existing.getDescription());
        r.setContactName(dto.getContactName() != null ? dto.getContactName() : existing.getContactName());
        r.setContactEmail(dto.getContactEmail() != null ? dto.getContactEmail() : existing.getContactEmail());
        r.setContactPhone(dto.getContactPhone() != null ? dto.getContactPhone() : existing.getContactPhone());
        return r;
    }
}
