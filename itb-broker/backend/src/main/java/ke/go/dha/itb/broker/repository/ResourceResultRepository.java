package ke.go.dha.itb.broker.repository;

import java.util.List;
import java.util.UUID;

import ke.go.dha.itb.broker.model.ResourceResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceResultRepository extends JpaRepository<ResourceResult, Long> {

    List<ResourceResult> findByTestSessionId(UUID testSessionId);
}
