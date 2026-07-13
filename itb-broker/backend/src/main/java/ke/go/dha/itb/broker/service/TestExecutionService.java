package ke.go.dha.itb.broker.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import ke.go.dha.itb.broker.dto.ITBWriteTestResult;
import ke.go.dha.itb.broker.dto.ScenarioDefinition;
import ke.go.dha.itb.broker.model.PortalTestCase;
import ke.go.dha.itb.broker.model.ResourceResult;
import ke.go.dha.itb.broker.model.SystemConfig;
import ke.go.dha.itb.broker.model.TestSession;
import ke.go.dha.itb.broker.model.enums.SessionStatus;
import ke.go.dha.itb.broker.model.enums.TestCaseType;
import ke.go.dha.itb.broker.repository.ResourceResultRepository;
import ke.go.dha.itb.broker.repository.TestSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class TestExecutionService {

    private static final Logger log = LoggerFactory.getLogger(TestExecutionService.class);

    private final TestSessionRepository sessionRepository;
    private final ResourceResultRepository resultRepository;
    private final ScenarioRegistryService scenarioRegistry;
    private final SutClient sutClient;
    private final ITBClient itbClient;
    private final SyntheticFhirGenerator syntheticFhirGenerator;
    private final CertificateService certificateService;
    private final PortalCallbackService portalCallbackService;

    public TestExecutionService(
        TestSessionRepository sessionRepository,
        ResourceResultRepository resultRepository,
        ScenarioRegistryService scenarioRegistry,
        SutClient sutClient,
        ITBClient itbClient,
        SyntheticFhirGenerator syntheticFhirGenerator,
        CertificateService certificateService,
        PortalCallbackService portalCallbackService
    ) {
        this.sessionRepository = sessionRepository;
        this.resultRepository = resultRepository;
        this.scenarioRegistry = scenarioRegistry;
        this.sutClient = sutClient;
        this.itbClient = itbClient;
        this.syntheticFhirGenerator = syntheticFhirGenerator;
        this.certificateService = certificateService;
        this.portalCallbackService = portalCallbackService;
    }

    @Async("brokerTaskExecutor")
    public CompletableFuture<Void> execute(UUID sessionId) {
        TestSession session = sessionRepository.findById(sessionId).orElseThrow();
        SystemConfig config = session.getSystemConfig();
        ScenarioDefinition scenario = scenarioRegistry.get(session.getTestScenario());

        session.setStatus(SessionStatus.RUNNING);
        session.setStartedAt(LocalDateTime.now());
        sessionRepository.save(session);

        try {
            String sutSystemId = config.getCertificationPortalSystemId();

            // Pre-create every testcase row up front (endpoints included) so the
            // full plan is visible immediately, before any of them actually run.
            List<ResourceResult> readResults = new ArrayList<>();
            for (String resourceType : scenario.getReadResources()) {
                String sutUrl = buildSutUrl(config.getSutBaseUrl(), resourceType, session.getPatientId());
                String itbUrl = itbClient.validationEndpoint(
                    session.getItbBaseUrl(), session.getItbSessionId(), resourceType, sutSystemId);
                readResults.add(createPendingResult(session, resourceType, TestCaseType.READ, sutUrl, itbUrl));
            }

            List<ResourceResult> writeResults = new ArrayList<>();
            if (session.isWriteTestEnabled()) {
                for (String resourceType : scenario.getWriteResources()) {
                    String itbUrl = itbClient.writeEndpoint(
                        session.getItbBaseUrl(), session.getItbSessionId(), resourceType, sutSystemId);
                    writeResults.add(createPendingResult(session, resourceType, TestCaseType.WRITE, null, itbUrl));
                }
            }

            for (ResourceResult result : readResults) {
                runReadTest(result, config.getAuthToken(), session.getItbBaseUrl(), session.getItbSessionId(), sutSystemId);
            }
            for (ResourceResult result : writeResults) {
                runWriteTest(result, session.getItbBaseUrl(), session.getItbSessionId(), sutSystemId);
            }

            finishSession(sessionId, session, config);

        } catch (Exception e) {
            session.setStatus(SessionStatus.FAILED);
            log.error("Test execution failed for session {}", sessionId, e);
        }

        session.setCompletedAt(LocalDateTime.now());
        sessionRepository.save(session);
        return CompletableFuture.completedFuture(null);
    }

    // Driven by a Certification Portal test request instead of the fixed YAML
    // scenario registry: resources and their explicit SUT endpoints come from
    // PortalTestCase rows (see PortalTestRequestController#start).
    @Async("brokerTaskExecutor")
    public CompletableFuture<Void> executePortalScenario(UUID sessionId, List<PortalTestCase> testCases) {
        TestSession session = sessionRepository.findById(sessionId).orElseThrow();
        SystemConfig config = session.getSystemConfig();

        session.setStatus(SessionStatus.RUNNING);
        session.setStartedAt(LocalDateTime.now());
        sessionRepository.save(session);

        try {
            String sutSystemId = config.getCertificationPortalSystemId();

            List<ResourceResult> results = new ArrayList<>();
            for (PortalTestCase testCase : testCases) {
                String itbUrl = testCase.getTestType() == TestCaseType.WRITE
                    ? itbClient.writeEndpoint(session.getItbBaseUrl(), session.getItbSessionId(), testCase.getResourceType(), sutSystemId)
                    : itbClient.validationEndpoint(session.getItbBaseUrl(), session.getItbSessionId(), testCase.getResourceType(), sutSystemId);
                results.add(createPendingResult(
                    session, testCase.getResourceType(), testCase.getTestType(), testCase.getEndpoint(), itbUrl));
            }

            for (ResourceResult result : results) {
                if (result.getTestType() == TestCaseType.READ) {
                    runReadTest(result, config.getAuthToken(), session.getItbBaseUrl(), session.getItbSessionId(), sutSystemId);
                } else {
                    runWriteTest(result, session.getItbBaseUrl(), session.getItbSessionId(), sutSystemId);
                }
            }

            finishSession(sessionId, session, config);

        } catch (Exception e) {
            session.setStatus(SessionStatus.FAILED);
            log.error("Portal-driven test execution failed for session {}", sessionId, e);
        }

        session.setCompletedAt(LocalDateTime.now());
        sessionRepository.save(session);
        return CompletableFuture.completedFuture(null);
    }

    // Re-runs a single already-created testcase row in place, using the
    // resourceType/endpoints it was created with — doesn't touch session status.
    @Async("brokerTaskExecutor")
    public CompletableFuture<Void> retryResult(Long resultId) {
        ResourceResult result = resultRepository.findById(resultId).orElseThrow();
        TestSession session = result.getTestSession();
        SystemConfig config = session.getSystemConfig();
        String sutSystemId = config.getCertificationPortalSystemId();

        if (result.getTestType() == TestCaseType.WRITE) {
            runWriteTest(result, session.getItbBaseUrl(), session.getItbSessionId(), sutSystemId);
        } else {
            runReadTest(result, config.getAuthToken(), session.getItbBaseUrl(), session.getItbSessionId(), sutSystemId);
        }
        return CompletableFuture.completedFuture(null);
    }

    private String buildSutUrl(String sutBaseUrl, String resourceType, String patientId) {
        return sutBaseUrl + "/" + resourceType
            + (patientId != null ? "?patient=" + patientId : "");
    }

    private ResourceResult createPendingResult(
        TestSession session, String resourceType, TestCaseType testType, String sutEndpoint, String itbEndpoint
    ) {
        ResourceResult result = new ResourceResult();
        result.setTestSession(session);
        result.setResourceType(resourceType);
        result.setTestType(testType);
        result.setSutEndpoint(sutEndpoint);
        result.setItbEndpoint(itbEndpoint);
        return resultRepository.save(result);
    }

    private void runReadTest(ResourceResult result, String authToken, String itbBaseUrl, String itbSessionId, String sutSystemId) {
        result.setTestedAt(LocalDateTime.now());

        String payload = null;
        try {
            payload = sutClient.fetchResourceFromUrl(result.getSutEndpoint(), authToken).block();
            result.setFetchStatus("200");
            result.setFetchedPayload(payload);
        } catch (WebClientResponseException e) {
            // 404 => SKIPPED in UI, 401 => token expired; both skip the ITB post
            result.setFetchStatus(String.valueOf(e.getStatusCode().value()));
        } catch (Exception e) {
            result.setFetchStatus("ERROR: " + e.getMessage());
        }

        if (payload != null) {
            try {
                String itbResponse = itbClient.postForValidation(
                    itbBaseUrl, itbSessionId, result.getResourceType(), payload, sutSystemId
                ).block();
                result.setItbPostStatus("200");
                result.setItbResponse(itbResponse);
            } catch (WebClientResponseException e) {
                result.setItbPostStatus("ERROR");
                result.setItbResponse(e.getResponseBodyAsString());
                log.error("ITB POST failed for {} in session {}: {}",
                    result.getResourceType(), result.getTestSession().getId(), e.getResponseBodyAsString());
            } catch (Exception e) {
                result.setItbPostStatus("ERROR");
                result.setItbResponse(e.getMessage());
                log.error("ITB POST failed for {} in session {}",
                    result.getResourceType(), result.getTestSession().getId(), e);
            }
        }
        resultRepository.save(result);
    }

    private void runWriteTest(ResourceResult result, String itbBaseUrl, String itbSessionId, String sutSystemId) {
        result.setTestedAt(LocalDateTime.now());

        String syntheticPayload;
        try {
            syntheticPayload = syntheticFhirGenerator.generate(result.getResourceType());
        } catch (IllegalArgumentException e) {
            log.warn("No synthetic template for {}, skipping write test", result.getResourceType());
            result.setWriteTestStatus("SKIPPED: no synthetic template");
            result.setWriteVerifyPassed(false);
            resultRepository.save(result);
            return;
        }

        try {
            ITBWriteTestResult writeResult = itbClient
                .postForWriteVerify(itbBaseUrl, itbSessionId, result.getResourceType(), syntheticPayload, sutSystemId)
                .block();

            result.setWriteTestStatus(writeResult.status());
            result.setWriteTestResponse(writeResult.rawResponse());
            result.setWriteVerifyPassed(writeResult.fieldsMatch());
            result.setWriteVerifyDiff(writeResult.diff());
        } catch (Exception e) {
            result.setWriteTestStatus("ERROR: " + e.getMessage());
            result.setWriteVerifyPassed(false);
            log.error("ITB write-verify failed for {} in session {}",
                result.getResourceType(), result.getTestSession().getId(), e);
        }
        resultRepository.save(result);
    }

    private void finishSession(UUID sessionId, TestSession session, SystemConfig config) {
        try {
            byte[] certBytes = itbClient
                .downloadCertificate(session.getItbBaseUrl(), session.getItbSessionId()).block();
            String certPath = certificateService.save(
                config.getSystemName(), session.getItbSessionId(), certBytes);
            session.setCertificatePath(certPath);

            try {
                portalCallbackService.upload(session, certBytes);
            } catch (Exception e) {
                log.error("Portal upload failed for session {} after retries", sessionId, e);
            }
        } catch (Exception e) {
            // Session still completes; UI shows a warning and allows manual retry
            session.setCertificatePath(null);
            log.error("Certificate download failed for session {}", sessionId, e);
        }

        session.setStatus(SessionStatus.COMPLETED);
    }
}
