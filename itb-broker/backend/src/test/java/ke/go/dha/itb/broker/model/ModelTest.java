package ke.go.dha.itb.broker.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import ke.go.dha.itb.broker.model.enums.AuthType;
import ke.go.dha.itb.broker.model.enums.SessionStatus;
import ke.go.dha.itb.broker.model.enums.TestCaseType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelTest {

    @Test
    void systemConfigHoldsAllFields() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        SystemConfig config = new SystemConfig();
        config.setId(id);
        config.setSystemName("Aga Khan EMR v2.1");
        config.setSutBaseUrl("https://emr.agakhan.co.ke/fhir");
        config.setOrganizationName("Aga Khan University Hospital");
        config.setSystemVersion("2.1.0");
        config.setAuthType(AuthType.BEARER);
        config.setAuthToken("token-123");
        config.setCertificationPortalSystemId("sys-00123");
        config.setCreatedAt(now);
        config.setUpdatedAt(now);

        assertThat(config.getId()).isEqualTo(id);
        assertThat(config.getSystemName()).isEqualTo("Aga Khan EMR v2.1");
        assertThat(config.getSutBaseUrl()).isEqualTo("https://emr.agakhan.co.ke/fhir");
        assertThat(config.getOrganizationName()).isEqualTo("Aga Khan University Hospital");
        assertThat(config.getSystemVersion()).isEqualTo("2.1.0");
        assertThat(config.getAuthType()).isEqualTo(AuthType.BEARER);
        assertThat(config.getAuthToken()).isEqualTo("token-123");
        assertThat(config.getCertificationPortalSystemId()).isEqualTo("sys-00123");
        assertThat(config.getCreatedAt()).isEqualTo(now);
        assertThat(config.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void testSessionHoldsAllFields() {
        UUID id = UUID.randomUUID();
        SystemConfig config = new SystemConfig();
        TestSession session = new TestSession();
        session.setId(id);
        session.setSystemConfig(config);
        session.setItbSessionId("78595BBDX5F0FX452DX8A34XE2FF49184EB3");
        session.setItbBaseUrl("http://itb-srv:8080");
        session.setTestScenario("PATIENT_SUMMARY");
        session.setPatientId("12345");
        session.setWriteTestEnabled(true);
        session.setStatus(SessionStatus.CONFIGURED);
        session.setCertificatePath("/app/certificates/x.pdf");
        LocalDateTime started = LocalDateTime.now();
        session.setStartedAt(started);
        session.setCompletedAt(started.plusMinutes(2));
        session.setResults(List.of());

        assertThat(session.getId()).isEqualTo(id);
        assertThat(session.getSystemConfig()).isSameAs(config);
        assertThat(session.getItbSessionId()).isEqualTo("78595BBDX5F0FX452DX8A34XE2FF49184EB3");
        assertThat(session.getItbBaseUrl()).isEqualTo("http://itb-srv:8080");
        assertThat(session.getTestScenario()).isEqualTo("PATIENT_SUMMARY");
        assertThat(session.getPatientId()).isEqualTo("12345");
        assertThat(session.isWriteTestEnabled()).isTrue();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.CONFIGURED);
        assertThat(session.getCertificatePath()).isEqualTo("/app/certificates/x.pdf");
        assertThat(session.getStartedAt()).isEqualTo(started);
        assertThat(session.getCompletedAt()).isEqualTo(started.plusMinutes(2));
        assertThat(session.getResults()).isEmpty();
    }

    @Test
    void resourceResultHoldsReadAndWriteFields() {
        TestSession session = new TestSession();
        LocalDateTime now = LocalDateTime.now();
        ResourceResult result = new ResourceResult();
        result.setId(7L);
        result.setTestSession(session);
        result.setResourceType("Patient");
        result.setTestType(TestCaseType.READ);
        result.setSutEndpoint("https://emr.example.ke/fhir/Patient?patient=12345");
        result.setItbEndpoint("http://itb-srv:8080/itbsrv/api/http/S1/patient");
        result.setFetchStatus("200");
        result.setFetchedPayload("{\"resourceType\":\"Patient\"}");
        result.setItbPostStatus("200");
        result.setItbResponse("{\"result\":\"VALID\"}");
        result.setWriteTestStatus("pass");
        result.setWriteTestResponse("{\"status\":\"pass\"}");
        result.setWriteVerifyPassed(false);
        result.setWriteVerifyDiff("Observation.status: missing in SUT response");
        result.setTestedAt(now);

        assertThat(result.getId()).isEqualTo(7L);
        assertThat(result.getTestSession()).isSameAs(session);
        assertThat(result.getResourceType()).isEqualTo("Patient");
        assertThat(result.getTestType()).isEqualTo(TestCaseType.READ);
        assertThat(result.getSutEndpoint()).contains("Patient");
        assertThat(result.getItbEndpoint()).contains("/itbsrv/api/http/");
        assertThat(result.getFetchStatus()).isEqualTo("200");
        assertThat(result.getFetchedPayload()).contains("Patient");
        assertThat(result.getItbPostStatus()).isEqualTo("200");
        assertThat(result.getItbResponse()).contains("VALID");
        assertThat(result.getWriteTestStatus()).isEqualTo("pass");
        assertThat(result.getWriteTestResponse()).contains("pass");
        assertThat(result.getWriteVerifyPassed()).isFalse();
        assertThat(result.getWriteVerifyDiff()).contains("Observation.status");
        assertThat(result.getTestedAt()).isEqualTo(now);
    }

    @Test
    void enumsExposeExpectedValues() {
        assertThat(AuthType.values()).containsExactly(AuthType.BEARER, AuthType.BASIC, AuthType.NONE);
        assertThat(SessionStatus.values()).containsExactly(
            SessionStatus.CONFIGURED, SessionStatus.RUNNING,
            SessionStatus.COMPLETED, SessionStatus.FAILED);
    }
}
