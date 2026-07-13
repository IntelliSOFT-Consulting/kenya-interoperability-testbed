package ke.go.dha.itb.broker.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import ke.go.dha.itb.broker.dto.PortalTestCasePayload;
import ke.go.dha.itb.broker.dto.PortalTestRequestPayload;
import ke.go.dha.itb.broker.dto.PortalTestScenarioPayload;
import ke.go.dha.itb.broker.dto.StartPortalRequestBody;
import ke.go.dha.itb.broker.model.PortalTestCase;
import ke.go.dha.itb.broker.model.PortalTestRequest;
import ke.go.dha.itb.broker.model.PortalTestScenario;
import ke.go.dha.itb.broker.model.SystemConfig;
import ke.go.dha.itb.broker.model.TestSession;
import ke.go.dha.itb.broker.model.enums.AuthType;
import ke.go.dha.itb.broker.model.enums.PortalRequestStatus;
import ke.go.dha.itb.broker.model.enums.SessionStatus;
import ke.go.dha.itb.broker.model.enums.TestCaseType;
import ke.go.dha.itb.broker.repository.PortalTestRequestRepository;
import ke.go.dha.itb.broker.repository.SystemConfigRepository;
import ke.go.dha.itb.broker.repository.TestSessionRepository;
import ke.go.dha.itb.broker.service.PortalCallbackService;
import ke.go.dha.itb.broker.service.TestExecutionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portal/test-requests")
public class PortalTestRequestController {

    private final PortalTestRequestRepository requestRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final TestSessionRepository sessionRepository;
    private final TestExecutionService testExecutionService;
    private final PortalCallbackService portalCallbackService;

    public PortalTestRequestController(
        PortalTestRequestRepository requestRepository,
        SystemConfigRepository systemConfigRepository,
        TestSessionRepository sessionRepository,
        TestExecutionService testExecutionService,
        PortalCallbackService portalCallbackService
    ) {
        this.requestRepository = requestRepository;
        this.systemConfigRepository = systemConfigRepository;
        this.sessionRepository = sessionRepository;
        this.testExecutionService = testExecutionService;
        this.portalCallbackService = portalCallbackService;
    }

    @PostMapping
    public ResponseEntity<PortalTestRequest> receive(@RequestBody PortalTestRequestPayload payload) {
        SystemConfig config = resolveSystemConfig(payload);

        PortalTestRequest request = new PortalTestRequest();
        request.setSystemConfig(config);
        request.setRequestId(payload.requestId());
        request.setSubmittedAt(LocalDateTime.now());
        request.setPatientId(payload.patientId());
        request.setStatus(PortalRequestStatus.PENDING);
        request.setCreatedAt(LocalDateTime.now());

        List<PortalTestScenario> scenarios = new ArrayList<>();
        for (PortalTestScenarioPayload scenarioPayload : payload.testScenarios()) {
            PortalTestScenario scenario = new PortalTestScenario();
            scenario.setPortalTestRequest(request);
            scenario.setScenarioKey(scenarioPayload.scenarioKey());

            List<PortalTestCase> testCases = new ArrayList<>();
            for (PortalTestCasePayload tc : scenarioPayload.testCases()) {
                PortalTestCase testCase = new PortalTestCase();
                testCase.setPortalTestScenario(scenario);
                testCase.setResourceType(tc.resourceType());
                testCase.setEndpoint(tc.endpoint());
                testCase.setTestType(TestCaseType.valueOf(tc.testType()));
                testCases.add(testCase);
            }
            scenario.setTestCases(testCases);
            scenarios.add(scenario);
        }
        request.setScenarios(scenarios);

        return ResponseEntity.status(HttpStatus.CREATED).body(requestRepository.save(request));
    }

    private SystemConfig resolveSystemConfig(PortalTestRequestPayload payload) {
        var systemInfo = payload.system();
        SystemConfig config = null;
        if (systemInfo.certificationPortalSystemId() != null
                && !systemInfo.certificationPortalSystemId().isBlank()) {
            config = systemConfigRepository
                .findByCertificationPortalSystemId(systemInfo.certificationPortalSystemId())
                .orElse(null);
        }
        if (config == null) {
            config = new SystemConfig();
            config.setCreatedAt(LocalDateTime.now());
        }
        config.setSystemName(systemInfo.name());
        config.setOrganizationName(systemInfo.organizationName());
        config.setSystemVersion(systemInfo.version());
        config.setCertificationPortalSystemId(systemInfo.certificationPortalSystemId());
        config.setAuthType(AuthType.valueOf(payload.auth().type()));
        config.setAuthToken(payload.auth().token());
        config.setUpdatedAt(LocalDateTime.now());
        return systemConfigRepository.save(config);
    }

    @GetMapping
    public List<PortalTestRequest> list() {
        return requestRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PortalTestRequest> get(@PathVariable UUID id) {
        return requestRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<PortalTestRequest> start(
        @PathVariable UUID id,
        @RequestBody StartPortalRequestBody body
    ) {
        PortalTestRequest request = requestRepository.findById(id).orElse(null);
        if (request == null) {
            return ResponseEntity.notFound().build();
        }
        if (request.getStatus() != PortalRequestStatus.PENDING) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        for (PortalTestScenario scenario : request.getScenarios()) {
            TestSession session = new TestSession();
            session.setSystemConfig(request.getSystemConfig());
            session.setItbSessionId(body.itbSessionId());
            session.setItbBaseUrl(body.itbBaseUrl());
            session.setTestScenario(scenario.getScenarioKey());
            session.setPatientId(request.getPatientId());
            session.setWriteTestEnabled(scenario.getTestCases().stream()
                .anyMatch(tc -> tc.getTestType() == TestCaseType.WRITE));
            session.setStatus(SessionStatus.CONFIGURED);
            session = sessionRepository.save(session);

            scenario.setTestSession(session);
            testExecutionService.executePortalScenario(session.getId(), scenario.getTestCases());
        }

        request.setStatus(PortalRequestStatus.STARTED);
        return ResponseEntity.ok(requestRepository.save(request));
    }

    @PostMapping("/{id}/send-status")
    public ResponseEntity<Void> sendStatus(@PathVariable UUID id) {
        PortalTestRequest request = requestRepository.findById(id).orElse(null);
        if (request == null) {
            return ResponseEntity.notFound().build();
        }
        portalCallbackService.sendStatus(request);
        return ResponseEntity.noContent().build();
    }
}
