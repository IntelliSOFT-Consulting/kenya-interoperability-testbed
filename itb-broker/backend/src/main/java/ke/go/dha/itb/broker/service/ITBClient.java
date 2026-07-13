package ke.go.dha.itb.broker.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.net.URLEncoder;

import ke.go.dha.itb.broker.dto.ITBWriteTestResult;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Service
public class ITBClient {

    private final WebClient.Builder builder;

    public ITBClient(WebClient.Builder builder) {
        this.builder = builder;
    }

    // ITB's WAR is deployed under context path /itbsrv (per the spec), but the
    // "waiting to receive" log line it prints is built from CALLBACK_ROOT_URL
    // and omits that context path in this deployment, which looks misleading
    // until you actually POST and see it 200 + advance the session. The
    // resource type segment must be lowercased — verified live against a real
    // gitb-srv session (ICL, "Receive new patient payload" step).
    //
    // itbBaseUrl is per-session (spec: "pre-filled from config, editable per
    // session"), not baked into the client at startup — a fixed WebClient
    // baseUrl would silently ignore an admin's per-session override.
    //
    // sutSystemId (the portal's certificationPortalSystemId, nullable) rides
    // along as a query parameter so TDL test cases can call the broker's
    // /api/sut-endpoints lookup and forward to the right real SUT, instead of
    // each TDL file hardcoding one fixed target server.
    public String validationEndpoint(String itbBaseUrl, String sessionId, String resourceType, String sutSystemId) {
        String base = itbBaseUrl + "/itbsrv/api/http/" + sessionId + "/" + resourceType.toLowerCase();
        return appendSutSystemParam(base, sutSystemId);
    }

    public String writeEndpoint(String itbBaseUrl, String sessionId, String resourceType, String sutSystemId) {
        String base = itbBaseUrl + "/itbsrv/api/http/" + sessionId + "/" + resourceType.toLowerCase() + "/write";
        return appendSutSystemParam(base, sutSystemId);
    }

    private String appendSutSystemParam(String url, String sutSystemId) {
        if (sutSystemId == null || sutSystemId.isBlank()) {
            return url;
        }
        return url + "?sutSystem=" + URLEncoder.encode(sutSystemId, StandardCharsets.UTF_8);
    }

    public Mono<String> postForValidation(
        String itbBaseUrl,
        String sessionId,
        String resourceType,
        String fhirPayload,
        String sutSystemId
    ) {
        return builder.build().post()
            .uri(validationEndpoint(itbBaseUrl, sessionId, resourceType, sutSystemId))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(fhirPayload)
            .retrieve()
            .bodyToMono(String.class);
    }

    public Mono<ITBWriteTestResult> postForWriteVerify(
        String itbBaseUrl,
        String sessionId,
        String resourceType,
        String syntheticFhirPayload,
        String sutSystemId
    ) {
        return builder.build().post()
            .uri(writeEndpoint(itbBaseUrl, sessionId, resourceType, sutSystemId))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(syntheticFhirPayload)
            .retrieve()
            .bodyToMono(ITBWriteTestResult.class);
    }

    // Poll up to 5 times with 10s delay: the certificate is generated
    // asynchronously by ITB after the session ends.
    public Mono<byte[]> downloadCertificate(String itbBaseUrl, String sessionId) {
        return builder.build().get()
            .uri(itbBaseUrl + "/itbsrv/api/rest/TestService/report?sessionId="
                + sessionId + "&format=PDF")
            .retrieve()
            .bodyToMono(byte[].class)
            .retryWhen(Retry.fixedDelay(5, Duration.ofSeconds(10)));
    }
}
