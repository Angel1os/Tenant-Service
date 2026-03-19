package ecdc.tenant.service.service;

import ecdc.tenant.service.domain.dto.TenantDto;
import ecdc.tenant.service.record.Response;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

public interface TenantService {

    ResponseEntity<Response> create(TenantDto tenantDto);

    ResponseEntity<Response> update(TenantDto tenantDto, UUID id);

    ResponseEntity<Response> findAll(Map<String, String> queryParams);

    ResponseEntity<Response> findById(UUID id);
}
