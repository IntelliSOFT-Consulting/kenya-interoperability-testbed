package ke.go.dha.itb.broker.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import ke.go.dha.itb.broker.dto.ITBWriteTestResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

class ITBClientTest {

    private static final String SESSION = "78595BBDX5F0FX452DX8A34XE2FF49184EB3";

    private WireMockServer server;
    private ITBClient itbClient;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
        itbClient = new ITBClient(WebClient.builder());
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void postsToValidationEndpointPattern() {
        server.stubFor(post(urlEqualTo("/itbsrv/api/http/" + SESSION + "/patient"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(equalToJson("{\"resourceType\":\"Patient\"}"))
            .willReturn(aResponse().withStatus(200)
                .withBody("{\"result\":\"VALID\"}")));

        String response = itbClient.postForValidation(
            server.baseUrl(), SESSION, "Patient", "{\"resourceType\":\"Patient\"}", null).block();

        assertThat(response).isEqualTo("{\"result\":\"VALID\"}");
    }

    @Test
    void postsToWriteEndpointAndParsesWriteTestResult() {
        server.stubFor(post(urlEqualTo("/itbsrv/api/http/" + SESSION + "/observation/write"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "status": "fail",
                      "resourceId": "obs-991",
                      "stored": true,
                      "fieldsMatch": false,
                      "diff": "Observation.status: missing in SUT response",
                      "rawResponse": "{\\"outcome\\":\\"mismatch\\"}"
                    }
                    """)));

        ITBWriteTestResult result = itbClient.postForWriteVerify(
            server.baseUrl(), SESSION, "Observation", "{\"resourceType\":\"Observation\"}", null).block();

        assertThat(result.status()).isEqualTo("fail");
        assertThat(result.resourceId()).isEqualTo("obs-991");
        assertThat(result.stored()).isTrue();
        assertThat(result.fieldsMatch()).isFalse();
        assertThat(result.diff()).contains("Observation.status");
        assertThat(result.rawResponse()).contains("mismatch");
    }

    @Test
    void downloadsCertificateFromReportEndpoint() {
        byte[] pdf = "%PDF-1.4 fake".getBytes();
        server.stubFor(get(urlEqualTo(
                "/itbsrv/api/rest/TestService/report?sessionId=" + SESSION + "&format=PDF"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/pdf")
                .withBody(pdf)));

        byte[] result = itbClient.downloadCertificate(server.baseUrl(), SESSION).block();

        assertThat(result).isEqualTo(pdf);
    }

    @Test
    void buildsEndpointsFromGivenBaseUrlNotAFixedOne() {
        assertThat(itbClient.validationEndpoint("http://itb-a:8080", SESSION, "Patient", null))
            .isEqualTo("http://itb-a:8080/itbsrv/api/http/" + SESSION + "/patient");
        assertThat(itbClient.validationEndpoint("http://itb-b:9090", SESSION, "Patient", null))
            .isEqualTo("http://itb-b:9090/itbsrv/api/http/" + SESSION + "/patient");
        assertThat(itbClient.writeEndpoint("http://itb-a:8080", SESSION, "Observation", null))
            .isEqualTo("http://itb-a:8080/itbsrv/api/http/" + SESSION + "/observation/write");
    }

    @Test
    void appendsSutSystemQueryParamWhenPresent() {
        assertThat(itbClient.validationEndpoint("http://itb-a:8080", SESSION, "Patient", "sys-00123"))
            .isEqualTo("http://itb-a:8080/itbsrv/api/http/" + SESSION + "/patient?sutSystem=sys-00123");
        assertThat(itbClient.writeEndpoint("http://itb-a:8080", SESSION, "Observation", "sys-00123"))
            .isEqualTo("http://itb-a:8080/itbsrv/api/http/" + SESSION + "/observation/write?sutSystem=sys-00123");
        assertThat(itbClient.validationEndpoint("http://itb-a:8080", SESSION, "Patient", ""))
            .isEqualTo("http://itb-a:8080/itbsrv/api/http/" + SESSION + "/patient");
    }
}
