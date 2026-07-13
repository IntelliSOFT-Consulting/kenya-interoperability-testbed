package ke.go.dha.itb.broker.repository;

import java.util.UUID;

import ke.go.dha.itb.broker.model.TestSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestSessionRepository extends JpaRepository<TestSession, UUID> {
}
