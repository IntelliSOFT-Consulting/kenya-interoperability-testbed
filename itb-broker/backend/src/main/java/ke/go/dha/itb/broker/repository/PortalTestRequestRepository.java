package ke.go.dha.itb.broker.repository;

import java.util.List;
import java.util.UUID;

import ke.go.dha.itb.broker.model.PortalTestRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortalTestRequestRepository extends JpaRepository<PortalTestRequest, UUID> {

    List<PortalTestRequest> findBySystemConfigIdOrderByCreatedAtDesc(UUID systemConfigId);
}
