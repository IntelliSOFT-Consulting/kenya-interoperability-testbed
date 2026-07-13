package ke.go.dha.itb.broker.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import ke.go.dha.itb.broker.dto.StartSessionRequest;
import ke.go.dha.itb.broker.model.ResourceResult;
import ke.go.dha.itb.broker.model.SystemConfig;
import ke.go.dha.itb.broker.model.TestSession;
import ke.go.dha.itb.broker.model.enums.SessionStatus;
import ke.go.dha.itb.broker.repository.ResourceResultRepository;
import ke.go.dha.itb.broker.repository.SystemConfigRepository;
import ke.go.dha.itb.broker.repository.TestSessionRepository;
import ke.go.dha.itb.broker.service.TestExecutionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
public class TestSessionController {

    private final TestSessionRepository sessionRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final ResourceResultRepository resultRepository;
    private final TestExecutionService testExecutionService;

    public TestSessionController(
        TestSessionRepository sessionRepository,
        SystemConfigRepository systemConfigRepository,
        ResourceResultRepository resultRepository,
        TestExecutionService testExecutionService
    ) {
        this.sessionRepository = sessionRepository;
        this.systemConfigRepository = systemConfigRepository;
        this.resultRepository = resultRepository;
        this.testExecutionService = testExecutionService;
    }

    @PostMapping
    public ResponseEntity<TestSession> createAndStart(@RequestBody StartSessionRequest request) {
        SystemConfig config = systemConfigRepository.findById(request.systemConfigId())
            .orElse(null);
        if (config == null) {
            return ResponseEntity.badRequest().build();
        }

        TestSession session = new TestSession();
        session.setSystemConfig(config);
        session.setItbSessionId(request.itbSessionId());
        session.setItbBaseUrl(request.itbBaseUrl());
        session.setTestScenario(request.testScenario());
        session.setPatientId(request.patientId());
        session.setWriteTestEnabled(request.writeTestEnabled());
        session.setStatus(SessionStatus.CONFIGURED);
        session = sessionRepository.save(session);

        testExecutionService.execute(session.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    @GetMapping
    public List<TestSession> list() {
        return sessionRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TestSession> get(@PathVariable UUID id) {
        return sessionRepository.findById(id)
            .map(session -> {
                session.setResults(resultRepository.findByTestSessionId(id));
                return ResponseEntity.ok(session);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/results")
    public ResponseEntity<List<ResourceResult>> results(@PathVariable UUID id) {
        if (!sessionRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(resultRepository.findByTestSessionId(id));
    }

    @PostMapping("/{id}/results/{resultId}/retry")
    public ResponseEntity<Void> retryResult(@PathVariable UUID id, @PathVariable Long resultId) {
        ResourceResult result = resultRepository.findById(resultId).orElse(null);
        if (result == null || result.getTestSession() == null
                || !id.equals(result.getTestSession().getId())) {
            return ResponseEntity.notFound().build();
        }
        testExecutionService.retryResult(resultId);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{id}/certificate")
    public ResponseEntity<byte[]> certificate(@PathVariable UUID id) throws IOException {
        TestSession session = sessionRepository.findById(id).orElse(null);
        if (session == null || session.getCertificatePath() == null) {
            return ResponseEntity.notFound().build();
        }
        Path path = Path.of(session.getCertificatePath());
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + path.getFileName() + "\"")
            .body(Files.readAllBytes(path));
    }
}
