package ke.go.dha.itb.broker.repository;

import java.util.Optional;
import java.util.UUID;

import ke.go.dha.itb.broker.model.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, UUID> {

    Optional<SystemConfig> findByCertificationPortalSystemId(String certificationPortalSystemId);
}
