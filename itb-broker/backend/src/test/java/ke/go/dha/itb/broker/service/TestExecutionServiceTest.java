package ke.go.dha.itb.broker.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestExecutionServiceTest {

    @Mock TestSessionRepository sessionRepository;
    @Mock ResourceResultRepository resultRepository;
    @Mock ScenarioRegistryService scenarioRegistry;
    @Mock SutClient sutClient;
    // Spy, not mock: validationEndpoint()/writeEndpoint() are pure URL-builders we
    // want exercised for real; only the HTTP-performing methods are stubbed below.
    @Spy ITBClient itbClient = new ITBClient(WebClient.builder());
    @Mock SyntheticFhirGenerator syntheticFhirGenerator;
    @Mock CertificateService certificateService;
    @Mock PortalCallbackService portalCallbackService;

    private TestExecutionService service;
    private TestSession session;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        service = new TestExecutionService(
            sessionRepository, resultRepository, scenarioRegistry, sutClient,
            itbClient, syntheticFhirGenerator, certificateService, portalCallbackService);

        SystemConfig config = new SystemConfig();
        config.setSystemName("Test EMR");
        config.setSutBaseUrl("http://sut/fhir");
        config.setAuthToken("tkn");

        sessionId = UUID.randomUUID();
        session = new TestSession();
        session.setId(sessionId);
        session.setSystemConfig(config);
        session.setItbSessionId("ITB-SESSION-1");
        session.setItbBaseUrl("http://itb-srv:8080");
        session.setTestScenario("PATIENT_SUMMARY");
        session.setPatientId("12345");

        // lenient: not every test exercises both (e.g. retryResult skips the
        // session lookup; empty-scenario tests never call resultRepository.save).
        org.mockito.Mockito.lenient().when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        // Every test now round-trips ResourceResult through save() at least twice
        // (pending create, then filled-in update) — echo the argument back so the
        // service's own in-memory references keep flowing correctly.
        org.mockito.Mockito.lenient().when(resultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ScenarioDefinition scenario(List<String> read, List<String> write) {
        ScenarioDefinition def = new ScenarioDefinition();
        def.setScenarioKey("PATIENT_SUMMARY");
        def.setReadResources(read);
        def.setWriteResources(write);
        return def;
    }

    @Test
    void happyPathCompletesSessionWithReadWriteAndCertificate() {
        session.setWriteTestEnabled(true);
        when(scenarioRegistry.get("PATIENT_SUMMARY"))
            .thenReturn(scenario(List.of("Patient"), List.of("Observation")));
        when(sutClient.fetchResourceFromUrl("http://sut/fhir/Patient?patient=12345", "tkn"))
            .thenReturn(Mono.just("{\"resourceType\":\"Patient\"}"));
        doReturn(Mono.just("{\"result\":\"VALID\"}")).when(itbClient)
            .postForValidation("http://itb-srv:8080", "ITB-SESSION-1", "Patient", "{\"resourceType\":\"Patient\"}", null);
        when(syntheticFhirGenerator.generate("Observation")).thenReturn("{\"resourceType\":\"Observation\"}");
        doReturn(Mono.just(new ITBWriteTestResult("pass", "obs-1", true, true, null, "{}")))
            .when(itbClient).postForWriteVerify(eq("http://itb-srv:8080"), eq("ITB-SESSION-1"), eq("Observation"), anyString(), isNull());
        doReturn(Mono.just("%PDF".getBytes())).when(itbClient)
            .downloadCertificate("http://itb-srv:8080", "ITB-SESSION-1");
        when(certificateService.save(eq("Test EMR"), eq("ITB-SESSION-1"), any()))
            .thenReturn("/app/certificates/Test_EMR_ITB-SESSION-1.pdf");

        service.execute(sessionId);

        ArgumentCaptor<ResourceResult> results = ArgumentCaptor.forClass(ResourceResult.class);
        verify(resultRepository, atLeastOnce()).save(results.capture());
        List<ResourceResult> saved = results.getAllValues().stream().distinct().toList();
        assertThat(saved).hasSize(2);

        ResourceResult read = saved.get(0);
        assertThat(read.getResourceType()).isEqualTo("Patient");
        assertThat(read.getTestType()).isEqualTo(TestCaseType.READ);
        assertThat(read.getSutEndpoint()).isEqualTo("http://sut/fhir/Patient?patient=12345");
        assertThat(read.getItbEndpoint()).isEqualTo("http://itb-srv:8080/itbsrv/api/http/ITB-SESSION-1/patient");
        assertThat(read.getFetchStatus()).isEqualTo("200");
        assertThat(read.getItbPostStatus()).isEqualTo("200");
        assertThat(read.getItbResponse()).contains("VALID");

        ResourceResult write = saved.get(1);
        assertThat(write.getResourceType()).isEqualTo("Observation");
        assertThat(write.getTestType()).isEqualTo(TestCaseType.WRITE);
        assertThat(write.getItbEndpoint()).isEqualTo("http://itb-srv:8080/itbsrv/api/http/ITB-SESSION-1/observation/write");
        assertThat(write.getWriteTestStatus()).isEqualTo("pass");
        assertThat(write.getWriteVerifyPassed()).isTrue();

        verify(portalCallbackService).upload(eq(session), any());
        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(session.getCertificatePath()).contains("Test_EMR");
        assertThat(session.getStartedAt()).isNotNull();
        assertThat(session.getCompletedAt()).isNotNull();
    }

    @Test
    void appendsSutSystemIdWhenCertificationPortalSystemIdIsSet() {
        session.getSystemConfig().setCertificationPortalSystemId("sys-00123");
        session.setWriteTestEnabled(false);
        when(scenarioRegistry.get("PATIENT_SUMMARY"))
            .thenReturn(scenario(List.of("Patient"), List.of()));
        when(sutClient.fetchResourceFromUrl(anyString(), anyString())).thenReturn(Mono.just("{}"));
        doReturn(Mono.just("{\"result\":\"VALID\"}")).when(itbClient)
            .postForValidation("http://itb-srv:8080", "ITB-SESSION-1", "Patient", "{}", "sys-00123");
        doReturn(Mono.just("%PDF".getBytes())).when(itbClient)
            .downloadCertificate(anyString(), anyString());
        when(certificateService.save(anyString(), anyString(), any())).thenReturn("/tmp/c.pdf");

        service.execute(sessionId);

        ArgumentCaptor<ResourceResult> results = ArgumentCaptor.forClass(ResourceResult.class);
        verify(resultRepository, atLeastOnce()).save(results.capture());
        ResourceResult saved = results.getAllValues().stream().distinct().findFirst().orElseThrow();
        assertThat(saved.getItbEndpoint())
            .isEqualTo("http://itb-srv:8080/itbsrv/api/http/ITB-SESSION-1/patient?sutSystem=sys-00123");
        assertThat(saved.getItbPostStatus()).isEqualTo("200");
        verify(itbClient).postForValidation("http://itb-srv:8080", "ITB-SESSION-1", "Patient", "{}", "sys-00123");
    }

    @Test
    void allTestcasesArePersistedBeforeAnyOfThemActuallyRun() {
        session.setWriteTestEnabled(false);
        when(scenarioRegistry.get("PATIENT_SUMMARY"))
            .thenReturn(scenario(List.of("Patient", "Condition"), List.of()));
        when(sutClient.fetchResourceFromUrl(anyString(), anyString())).thenReturn(Mono.just("{}"));
        doReturn(Mono.just("{\"result\":\"VALID\"}")).when(itbClient)
            .postForValidation(anyString(), anyString(), anyString(), anyString(), isNull());
        doReturn(Mono.just("%PDF".getBytes())).when(itbClient)
            .downloadCertificate(anyString(), anyString());
        when(certificateService.save(anyString(), anyString(), any())).thenReturn("/tmp/c.pdf");

        InOrder inOrder = inOrder(resultRepository, sutClient);

        service.execute(sessionId);

        // Both pending rows (Patient, Condition) must be saved before the first
        // actual SUT fetch — that's what makes the full testcase list visible
        // to a poller immediately, not filled in one at a time.
        inOrder.verify(resultRepository, times(2)).save(any());
        inOrder.verify(sutClient, atLeastOnce()).fetchResourceFromUrl(anyString(), anyString());
    }

    @Test
    void sut404SkipsItbPostAndContinues() {
        session.setWriteTestEnabled(false);
        when(scenarioRegistry.get("PATIENT_SUMMARY"))
            .thenReturn(scenario(List.of("MedicationStatement", "Observation"), List.of()));
        when(sutClient.fetchResourceFromUrl(eq("http://sut/fhir/MedicationStatement?patient=12345"), anyString()))
            .thenReturn(Mono.error(WebClientResponseException.create(
                404, "Not Found", HttpHeaders.EMPTY, new byte[0], null)));
        when(sutClient.fetchResourceFromUrl(eq("http://sut/fhir/Observation?patient=12345"), anyString()))
            .thenReturn(Mono.just("{}"));
        doReturn(Mono.just("{\"result\":\"VALID\"}")).when(itbClient)
            .postForValidation(anyString(), anyString(), eq("Observation"), anyString(), isNull());
        doReturn(Mono.just("%PDF".getBytes())).when(itbClient)
            .downloadCertificate(anyString(), anyString());
        when(certificateService.save(anyString(), anyString(), any())).thenReturn("/tmp/c.pdf");

        service.execute(sessionId);

        ArgumentCaptor<ResourceResult> results = ArgumentCaptor.forClass(ResourceResult.class);
        verify(resultRepository, atLeastOnce()).save(results.capture());
        List<ResourceResult> saved = results.getAllValues().stream().distinct().toList();
        ResourceResult missing = saved.stream()
            .filter(r -> r.getResourceType().equals("MedicationStatement")).findFirst().orElseThrow();
        assertThat(missing.getFetchStatus()).isEqualTo("404");
        assertThat(missing.getItbPostStatus()).isNull();
        verify(itbClient, never()).postForValidation(anyString(), anyString(), eq("MedicationStatement"), anyString(), any());
        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
    }

    @Test
    void sut401RecordsTokenExpiry() {
        session.setWriteTestEnabled(false);
        when(scenarioRegistry.get("PATIENT_SUMMARY"))
            .thenReturn(scenario(List.of("Patient"), List.of()));
        when(sutClient.fetchResourceFromUrl(anyString(), anyString()))
            .thenReturn(Mono.error(WebClientResponseException.create(
                401, "Unauthorized", HttpHeaders.EMPTY, new byte[0], null)));
        doReturn(Mono.just("%PDF".getBytes())).when(itbClient)
            .downloadCertificate(anyString(), anyString());
        when(certificateService.save(anyString(), anyString(), any())).thenReturn("/tmp/c.pdf");

        service.execute(sessionId);

        ArgumentCaptor<ResourceResult> results = ArgumentCaptor.forClass(ResourceResult.class);
        verify(resultRepository, atLeastOnce()).save(results.capture());
        ResourceResult saved = results.getAllValues().stream().distinct().findFirst().orElseThrow();
        assertThat(saved.getFetchStatus()).isEqualTo("401");
    }

    @Test
    void itbPostFailureRecordsErrorAndContinues() {
        session.setWriteTestEnabled(false);
        when(scenarioRegistry.get("PATIENT_SUMMARY"))
            .thenReturn(scenario(List.of("Patient"), List.of()));
        when(sutClient.fetchResourceFromUrl(anyString(), anyString())).thenReturn(Mono.just("{}"));
        doReturn(Mono.error(WebClientResponseException.create(
                500, "Server Error", HttpHeaders.EMPTY, "itb boom".getBytes(), null)))
            .when(itbClient).postForValidation(anyString(), anyString(), anyString(), anyString(), isNull());
        doReturn(Mono.just("%PDF".getBytes())).when(itbClient)
            .downloadCertificate(anyString(), anyString());
        when(certificateService.save(anyString(), anyString(), any())).thenReturn("/tmp/c.pdf");

        service.execute(sessionId);

        ArgumentCaptor<ResourceResult> results = ArgumentCaptor.forClass(ResourceResult.class);
        verify(resultRepository, atLeastOnce()).save(results.capture());
        ResourceResult saved = results.getAllValues().stream().distinct().findFirst().orElseThrow();
        assertThat(saved.getItbPostStatus()).isEqualTo("ERROR");
        assertThat(saved.getItbResponse()).contains("itb boom");
        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
    }

    @Test
    void writeVerifyErrorMarksFailedResultButNotSession() {
        session.setWriteTestEnabled(true);
        when(scenarioRegistry.get("PATIENT_SUMMARY"))
            .thenReturn(scenario(List.of(), List.of("Patient")));
        when(syntheticFhirGenerator.generate("Patient")).thenReturn("{}");
        doReturn(Mono.error(new RuntimeException("write endpoint down"))).when(itbClient)
            .postForWriteVerify(anyString(), anyString(), anyString(), anyString(), isNull());
        doReturn(Mono.just("%PDF".getBytes())).when(itbClient)
            .downloadCertificate(anyString(), anyString());
        when(certificateService.save(anyString(), anyString(), any())).thenReturn("/tmp/c.pdf");

        service.execute(sessionId);

        ArgumentCaptor<ResourceResult> results = ArgumentCaptor.forClass(ResourceResult.class);
        verify(resultRepository, atLeastOnce()).save(results.capture());
        ResourceResult saved = results.getAllValues().stream().distinct().findFirst().orElseThrow();
        assertThat(saved.getWriteTestStatus()).startsWith("ERROR");
        assertThat(saved.getWriteVerifyPassed()).isFalse();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
    }

    @Test
    void missingSyntheticTemplateStillCreatesRowButMarksItSkipped() {
        session.setWriteTestEnabled(true);
        when(scenarioRegistry.get("PATIENT_SUMMARY"))
            .thenReturn(scenario(List.of(), List.of("Specimen")));
        when(syntheticFhirGenerator.generate("Specimen"))
            .thenThrow(new IllegalArgumentException("No synthetic template for: Specimen"));
        doReturn(Mono.just("%PDF".getBytes())).when(itbClient)
            .downloadCertificate(anyString(), anyString());
        when(certificateService.save(anyString(), anyString(), any())).thenReturn("/tmp/c.pdf");

        service.execute(sessionId);

        verify(itbClient, never()).postForWriteVerify(anyString(), anyString(), anyString(), anyString(), any());
        ArgumentCaptor<ResourceResult> results = ArgumentCaptor.forClass(ResourceResult.class);
        verify(resultRepository, atLeastOnce()).save(results.capture());
        ResourceResult saved = results.getAllValues().stream().distinct().findFirst().orElseThrow();
        assertThat(saved.getWriteTestStatus()).startsWith("SKIPPED");
        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
    }

    @Test
    void certificateDownloadFailureStillCompletesSessionWithoutPath() {
        session.setWriteTestEnabled(false);
        when(scenarioRegistry.get("PATIENT_SUMMARY"))
            .thenReturn(scenario(List.of(), List.of()));
        doReturn(Mono.error(new RuntimeException("retries exhausted"))).when(itbClient)
            .downloadCertificate(anyString(), anyString());

        service.execute(sessionId);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(session.getCertificatePath()).isNull();
        verify(portalCallbackService, never()).upload(any(), any());
    }

    @Test
    void portalUploadFailureStillCompletesSession() {
        session.setWriteTestEnabled(false);
        when(scenarioRegistry.get("PATIENT_SUMMARY"))
            .thenReturn(scenario(List.of(), List.of()));
        doReturn(Mono.just("%PDF".getBytes())).when(itbClient)
            .downloadCertificate(anyString(), anyString());
        when(certificateService.save(anyString(), anyString(), any())).thenReturn("/tmp/c.pdf");
        org.mockito.Mockito.doThrow(new RuntimeException("portal down"))
            .when(portalCallbackService).upload(any(), any());

        service.execute(sessionId);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(session.getCertificatePath()).isEqualTo("/tmp/c.pdf");
    }

    @Test
    void portalScenarioRoutesReadAndWriteTestCasesByType() {
        PortalTestCase readCase = new PortalTestCase();
        readCase.setResourceType("Patient");
        readCase.setEndpoint("https://emr.example.ke/fhir/patDemo");
        readCase.setTestType(TestCaseType.READ);

        PortalTestCase writeCase = new PortalTestCase();
        writeCase.setResourceType("Observation");
        writeCase.setTestType(TestCaseType.WRITE);

        when(sutClient.fetchResourceFromUrl("https://emr.example.ke/fhir/patDemo", "tkn"))
            .thenReturn(Mono.just("{\"resourceType\":\"Patient\"}"));
        doReturn(Mono.just("{\"result\":\"VALID\"}")).when(itbClient)
            .postForValidation("http://itb-srv:8080", "ITB-SESSION-1", "Patient", "{\"resourceType\":\"Patient\"}", null);
        when(syntheticFhirGenerator.generate("Observation")).thenReturn("{\"resourceType\":\"Observation\"}");
        doReturn(Mono.just(new ITBWriteTestResult("pass", "obs-1", true, true, null, "{}")))
            .when(itbClient).postForWriteVerify(eq("http://itb-srv:8080"), eq("ITB-SESSION-1"), eq("Observation"), anyString(), isNull());
        doReturn(Mono.just("%PDF".getBytes())).when(itbClient)
            .downloadCertificate("http://itb-srv:8080", "ITB-SESSION-1");
        when(certificateService.save(eq("Test EMR"), eq("ITB-SESSION-1"), any()))
            .thenReturn("/app/certificates/Test_EMR_ITB-SESSION-1.pdf");

        service.executePortalScenario(sessionId, List.of(readCase, writeCase));

        ArgumentCaptor<ResourceResult> results = ArgumentCaptor.forClass(ResourceResult.class);
        verify(resultRepository, atLeastOnce()).save(results.capture());
        List<ResourceResult> saved = results.getAllValues().stream().distinct().toList();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getResourceType()).isEqualTo("Patient");
        assertThat(saved.get(0).getSutEndpoint()).isEqualTo("https://emr.example.ke/fhir/patDemo");
        assertThat(saved.get(0).getFetchStatus()).isEqualTo("200");
        assertThat(saved.get(1).getResourceType()).isEqualTo("Observation");
        assertThat(saved.get(1).getWriteVerifyPassed()).isTrue();

        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        verify(scenarioRegistry, never()).get(anyString());
    }

    @Test
    void retryResultReRunsInPlaceWithoutTouchingSessionStatus() {
        ResourceResult existing = new ResourceResult();
        existing.setId(42L);
        existing.setTestSession(session);
        existing.setResourceType("Patient");
        existing.setTestType(TestCaseType.READ);
        existing.setSutEndpoint("http://sut/fhir/Patient?patient=12345");
        existing.setFetchStatus("ERROR: timeout");
        when(resultRepository.findById(42L)).thenReturn(Optional.of(existing));
        when(sutClient.fetchResourceFromUrl("http://sut/fhir/Patient?patient=12345", "tkn"))
            .thenReturn(Mono.just("{\"resourceType\":\"Patient\"}"));
        doReturn(Mono.just("{\"result\":\"VALID\"}")).when(itbClient)
            .postForValidation("http://itb-srv:8080", "ITB-SESSION-1", "Patient", "{\"resourceType\":\"Patient\"}", null);

        service.retryResult(42L);

        assertThat(existing.getFetchStatus()).isEqualTo("200");
        assertThat(existing.getItbPostStatus()).isEqualTo("200");
        assertThat(session.getStatus()).isNull();
        verify(sessionRepository, never()).save(any());
    }
}
