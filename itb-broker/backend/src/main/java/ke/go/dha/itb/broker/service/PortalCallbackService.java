package ke.go.dha.itb.broker.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import ke.go.dha.itb.broker.config.BrokerProperties;
import ke.go.dha.itb.broker.dto.PortalStatusReport;
import ke.go.dha.itb.broker.model.PortalTestRequest;
import ke.go.dha.itb.broker.model.PortalTestScenario;
import ke.go.dha.itb.broker.model.ResourceResult;
import ke.go.dha.itb.broker.model.SystemConfig;
import ke.go.dha.itb.broker.model.TestSession;
import ke.go.dha.itb.broker.repository.ResourceResultRepository;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

@Service
public class PortalCallbackService {

    private final WebClient webClient;
    private final BrokerProperties properties;
    private final ResourceResultRepository resultRepository;

    public PortalCallbackService(
        WebClient.Builder builder,
        BrokerProperties properties,
        ResourceResultRepository resultRepository
    ) {
        this.webClient = builder.baseUrl(properties.getPortal().getBaseUrl()).build();
        this.properties = properties;
        this.resultRepository = resultRepository;
    }

    public void upload(TestSession session, byte[] certBytes) {
        String systemId = session.getSystemConfig().getCertificationPortalSystemId();

        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("certificate", new ByteArrayResource(certBytes) {
                @Override
                public String getFilename() {
                    return session.getItbSessionId() + ".pdf";
                }
            })
            .contentType(MediaType.APPLICATION_PDF);
        body.part("systemId", systemId != null ? systemId : "");
        body.part("itbSessionId", session.getItbSessionId());
        body.part("testScenario", session.getTestScenario());

        webClient.post()
            .uri("/api/certificates")
            .header("X-API-Key", properties.getPortal().getApiKey())
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(body.build()))
            .retrieve()
            .bodyToMono(String.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(5)))
            .block();
    }

    // Placeholder endpoint path pending the real Certification Portal API contract
    // (same situation as /api/certificates above) — pass/fail summary, not the PDF itself.
    public void sendStatus(PortalTestRequest request) {
        PortalStatusReport report = buildStatusReport(request);

        webClient.post()
            .uri("/api/test-status")
            .header("X-API-Key", properties.getPortal().getApiKey())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(report)
            .retrieve()
            .bodyToMono(String.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(5)))
            .block();
    }

    private PortalStatusReport buildStatusReport(PortalTestRequest request) {
        SystemConfig config = request.getSystemConfig();

        List<PortalStatusReport.ScenarioStatus> scenarioStatuses = new ArrayList<>();
        for (PortalTestScenario scenario : request.getScenarios()) {
            TestSession session = scenario.getTestSession();
            if (session == null) {
                scenarioStatuses.add(new PortalStatusReport.ScenarioStatus(
                    scenario.getScenarioKey(), "NOT_STARTED", null, 0, 0, 0, 0, false));
                continue;
            }

            List<ResourceResult> results = resultRepository.findByTestSessionId(session.getId());
            long readTotal = results.stream().filter(r -> r.getFetchStatus() != null).count();
            long readPassed = results.stream()
                .filter(r -> "200".equals(r.getFetchStatus()) && "200".equals(r.getItbPostStatus()))
                .count();
            long writeTotal = results.stream().filter(r -> r.getWriteTestStatus() != null).count();
            long writePassed = results.stream()
                .filter(r -> Boolean.TRUE.equals(r.getWriteVerifyPassed()))
                .count();

            scenarioStatuses.add(new PortalStatusReport.ScenarioStatus(
                scenario.getScenarioKey(),
                session.getStatus().name(),
                session.getItbSessionId(),
                readPassed, readTotal, writePassed, writeTotal,
                session.getCertificatePath() != null));
        }

        return new PortalStatusReport(
            request.getRequestId(),
            config.getOrganizationName(),
            config.getSystemName(),
            config.getSystemVersion(),
            config.getCertificationPortalSystemId(),
            scenarioStatuses);
    }
}
