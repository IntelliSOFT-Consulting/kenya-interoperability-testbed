package ke.go.dha.itb.broker.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SutClientTest {

    private WireMockServer server;
    private SutClient sutClient;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
        sutClient = new SutClient(WebClient.builder());
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void fetchesResourceWithBearerAuthAndPatientScope() {
        server.stubFor(get(urlEqualTo("/Patient?patient=12345"))
            .withHeader("Authorization", equalTo("Bearer tkn-1"))
            .withHeader("Accept", equalTo("application/fhir+json"))
            .willReturn(aResponse().withStatus(200)
                .withBody("{\"resourceType\":\"Bundle\"}")));

        String body = sutClient.fetchResource(
            server.baseUrl(), "Patient", "12345", "tkn-1").block();

        assertThat(body).isEqualTo("{\"resourceType\":\"Bundle\"}");
    }

    @Test
    void omitsPatientParamWhenPatientIdIsNull() {
        server.stubFor(get(urlEqualTo("/Condition"))
            .willReturn(aResponse().withStatus(200).withBody("{}")));

        String body = sutClient.fetchResource(
            server.baseUrl(), "Condition", null, "tkn-1").block();

        assertThat(body).isEqualTo("{}");
    }

    @Test
    void propagates404AsWebClientResponseException() {
        server.stubFor(get(urlEqualTo("/MedicationStatement?patient=12345"))
            .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> sutClient.fetchResource(
                server.baseUrl(), "MedicationStatement", "12345", "tkn-1").block())
            .isInstanceOf(WebClientResponseException.class)
            .satisfies(e -> assertThat(
                ((WebClientResponseException) e).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void fetchesFromExplicitPortalSuppliedEndpoint() {
        server.stubFor(get(urlEqualTo("/patDemo"))
            .withHeader("Authorization", equalTo("Bearer tkn-2"))
            .willReturn(aResponse().withStatus(200)
                .withBody("{\"resourceType\":\"Patient\"}")));

        String body = sutClient.fetchResourceFromUrl(
            server.baseUrl() + "/patDemo", "tkn-2").block();

        assertThat(body).isEqualTo("{\"resourceType\":\"Patient\"}");
    }
}
