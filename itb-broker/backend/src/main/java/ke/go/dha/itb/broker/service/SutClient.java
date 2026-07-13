package ke.go.dha.itb.broker.service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class SutClient {

    private final WebClient.Builder builder;

    public SutClient(WebClient.Builder builder) {
        this.builder = builder;
    }

    public Mono<String> fetchResource(
        String sutBaseUrl,
        String resourceType,
        String patientId,
        String authToken
    ) {
        String uri = sutBaseUrl + "/" + resourceType
            + (patientId != null ? "?patient=" + patientId : "");

        return builder.build()
            .get()
            .uri(uri)
            .header("Authorization", "Bearer " + authToken)
            .header("Accept", "application/fhir+json")
            .retrieve()
            .bodyToMono(String.class);
    }

    // Portal-submitted testcases carry the exact SUT URL per resource, since
    // SUTs frequently don't expose FHIR-conventional {baseUrl}/{ResourceType} paths.
    public Mono<String> fetchResourceFromUrl(String url, String authToken) {
        return builder.build()
            .get()
            .uri(url)
            .header("Authorization", "Bearer " + authToken)
            .header("Accept", "application/fhir+json")
            .retrieve()
            .bodyToMono(String.class);
    }
}
