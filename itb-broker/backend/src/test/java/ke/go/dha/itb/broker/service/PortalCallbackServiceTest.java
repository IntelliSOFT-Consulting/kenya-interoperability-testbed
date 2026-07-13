package ke.go.dha.itb.broker.service;

import java.util.List;
import java.util.UUID;

import com.github.tomakehurst.wiremock.WireMockServer;
import ke.go.dha.itb.broker.config.BrokerProperties;
import ke.go.dha.itb.broker.model.PortalTestRequest;
import ke.go.dha.itb.broker.model.PortalTestScenario;
import ke.go.dha.itb.broker.model.ResourceResult;
import ke.go.dha.itb.broker.model.SystemConfig;
import ke.go.dha.itb.broker.model.TestSession;
import ke.go.dha.itb.broker.model.enums.SessionStatus;
import ke.go.dha.itb.broker.repository.ResourceResultRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortalCallbackServiceTest {

    private WireMockServer server;
    private ResourceResultRepository resultRepository;
    private PortalCallbackService service;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
        BrokerProperties properties = new BrokerProperties();
        properties.getPortal().setBaseUrl(server.baseUrl());
        properties.getPortal().setApiKey("portal-key-1");
        resultRepository = mock(ResourceResultRepository.class);
        service = new PortalCallbackService(WebClient.builder(), properties, resultRepository);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void uploadsCertificateAsMultipartWithSessionMetadata() {
        server.stubFor(post(urlEqualTo("/api/certificates"))
            .willReturn(aResponse().withStatus(200).withBody("{\"ok\":true}")));

        SystemConfig config = new SystemConfig();
        config.setCertificationPortalSystemId("sys-00123");
        TestSession session = new TestSession();
        session.setSystemConfig(config);
        session.setItbSessionId("SESSION123");
        session.setTestScenario("PATIENT_SUMMARY");

        service.upload(session, "%PDF-1.4 cert".getBytes());

        server.verify(postRequestedFor(urlEqualTo("/api/certificates"))
            .withHeader("X-API-Key", equalTo("portal-key-1"))
            .withHeader("Content-Type", containing("multipart/form-data"))
            .withRequestBody(containing("sys-00123"))
            .withRequestBody(containing("SESSION123"))
            .withRequestBody(containing("PATIENT_SUMMARY"))
            .withRequestBody(containing("%PDF-1.4 cert")));
    }

    @Test
    void sendsStatusReportSummarizingEachScenario() {
        server.stubFor(post(urlEqualTo("/api/test-status"))
            .willReturn(aResponse().withStatus(200).withBody("{\"ok\":true}")));

        SystemConfig config = new SystemConfig();
        config.setSystemName("Aga Khan EMR v2.1");
        config.setOrganizationName("Aga Khan University Hospital");
        config.setSystemVersion("2.1.0");
        config.setCertificationPortalSystemId("sys-00123");

        PortalTestRequest request = new PortalTestRequest();
        request.setRequestId("req-1");
        request.setSystemConfig(config);

        UUID sessionId = UUID.randomUUID();
        TestSession session = new TestSession();
        session.setId(sessionId);
        session.setItbSessionId("ITB-SESSION-1");
        session.setStatus(SessionStatus.COMPLETED);
        session.setCertificatePath("/app/certificates/x.pdf");

        PortalTestScenario scenario = new PortalTestScenario();
        scenario.setScenarioKey("PATIENT_SUMMARY");
        scenario.setTestSession(session);
        request.setScenarios(List.of(scenario));

        ResourceResult read = new ResourceResult();
        read.setFetchStatus("200");
        read.setItbPostStatus("200");
        ResourceResult write = new ResourceResult();
        write.setWriteTestStatus("pass");
        write.setWriteVerifyPassed(true);
        when(resultRepository.findByTestSessionId(sessionId)).thenReturn(List.of(read, write));

        service.sendStatus(request);

        server.verify(postRequestedFor(urlEqualTo("/api/test-status"))
            .withHeader("X-API-Key", equalTo("portal-key-1"))
            .withRequestBody(containing("sys-00123"))
            .withRequestBody(containing("Aga Khan University Hospital"))
            .withRequestBody(containing("PATIENT_SUMMARY"))
            .withRequestBody(containing("ITB-SESSION-1"))
            .withRequestBody(containing("COMPLETED")));
    }
}
