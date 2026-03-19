package ecdc.tenant.service.repository;


import ecdc.tenant.service.domain.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    Optional<Tenant> findByCode(String code);
    Optional<Tenant> findByTenantKey(String tenantKey);
    Optional<Tenant> findByParticipantDid(String participantDid);
    Optional<Tenant> findByParticipantBpn(String participantBpn);
}
