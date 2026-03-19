package ecdc.tenant.service.service.impl;

import ecdc.tenant.service.domain.dto.TenantDto;
import ecdc.tenant.service.domain.model.Tenant;
import ecdc.tenant.service.enums.StatusCode;
import ecdc.tenant.service.record.Response;
import ecdc.tenant.service.repository.TenantRepository;
import ecdc.tenant.service.service.TenantService;
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

    @Override
    public ResponseEntity<Response> create(TenantDto tenantDto) {
        log.info("inside create tenant :: {}", tenantDto);
        validateUniqueTenantFields(tenantDto, null);
        Tenant tenant = tenantDto.toEntity(new Tenant());
        Tenant savedTenant = tenantRepository.save(tenant);
        return getResponse(SAVED_MESSAGE, HttpStatus.CREATED, StatusCode.SUCCESS, savedTenant.toDto()).toResponseEntity();
    }

    @Override
    public ResponseEntity<Response> update(TenantDto tenantDto, UUID id) {
        log.info("inside update tenant with id :: {} and payload :: {}", id, tenantDto);
        Tenant existingTenant = tenantRepository.findById(id).orElse(null);

        if (isNotNullOrEmpty(existingTenant)) {
            validateUniqueTenantFields(tenantDto, id);
            Tenant updatedEntity = tenantDto.toEntity(existingTenant);
            Tenant updatedTenant = tenantRepository.save(updatedEntity);
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

    private void validateUniqueTenantFields(TenantDto dto, UUID currentTenantId) {
        assertUnique(
                tenantRepository.findByCode(dto.getCode()),
                currentTenantId,
                "Tenant code already exists"
        );
        assertUnique(
                tenantRepository.findByTenantKey(dto.getTenantKey()),
                currentTenantId,
                "Tenant key already exists"
        );
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
}
