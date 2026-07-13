package ke.go.dha.itb.broker.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import ke.go.dha.itb.broker.model.ResourceResult;
import ke.go.dha.itb.broker.model.SystemConfig;
import ke.go.dha.itb.broker.model.TestSession;
import ke.go.dha.itb.broker.model.enums.SessionStatus;
import ke.go.dha.itb.broker.repository.ResourceResultRepository;
import ke.go.dha.itb.broker.repository.SystemConfigRepository;
import ke.go.dha.itb.broker.repository.TestSessionRepository;
import ke.go.dha.itb.broker.service.TestExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TestSessionController.class)
class TestSessionControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean TestSessionRepository sessionRepository;
    @MockBean SystemConfigRepository systemConfigRepository;
    @MockBean ResourceResultRepository resultRepository;
    @MockBean TestExecutionService testExecutionService;

    @TempDir
    Path tempDir;

    @Test
    void createStartsExecutionAndReturns201() throws Exception {
        UUID configId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        SystemConfig config = new SystemConfig();
        config.setId(configId);
        when(systemConfigRepository.findById(configId)).thenReturn(Optional.of(config));
        when(sessionRepository.save(any())).thenAnswer(inv -> {
            TestSession s = inv.getArgument(0);
            s.setId(sessionId);
            return s;
        });

        mockMvc.perform(post("/api/sessions")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "systemConfigId": "%s",
                      "itbSessionId": "78595BBDX5F0FX452DX8A34XE2FF49184EB3",
                      "itbBaseUrl": "http://itb-srv:8080",
                      "testScenario": "PATIENT_SUMMARY",
                      "patientId": "12345",
                      "writeTestEnabled": true
                    }
                    """.formatted(configId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(sessionId.toString()))
            .andExpect(jsonPath("$.itbSessionId").value("78595BBDX5F0FX452DX8A34XE2FF49184EB3"))
            .andExpect(jsonPath("$.status").value("CONFIGURED"))
            .andExpect(jsonPath("$.writeTestEnabled").value(true));

        verify(testExecutionService).execute(sessionId);
    }

    @Test
    void createWithUnknownSystemReturns400() throws Exception {
        when(systemConfigRepository.findById(any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/sessions")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "systemConfigId": "%s",
                      "itbSessionId": "X",
                      "itbBaseUrl": "http://itb-srv:8080",
                      "testScenario": "LAB",
                      "writeTestEnabled": false
                    }
                    """.formatted(UUID.randomUUID())))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getReturnsSessionWithResults() throws Exception {
        UUID sessionId = UUID.randomUUID();
        TestSession session = new TestSession();
        session.setId(sessionId);
        session.setItbSessionId("S1");
        session.setItbBaseUrl("http://itb-srv:8080");
        session.setTestScenario("LAB");
        session.setStatus(SessionStatus.COMPLETED);
        ResourceResult result = new ResourceResult();
        result.setResourceType("Patient");
        result.setFetchStatus("200");
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(resultRepository.findByTestSessionId(sessionId)).thenReturn(List.of(result));

        mockMvc.perform(get("/api/sessions/" + sessionId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.results[0].resourceType").value("Patient"))
            .andExpect(jsonPath("$.results[0].fetchStatus").value("200"));
    }

    @Test
    void listReturnsAllSessions() throws Exception {
        TestSession session = new TestSession();
        session.setItbSessionId("S1");
        session.setItbBaseUrl("http://itb-srv:8080");
        session.setTestScenario("ECLAIMS");
        session.setStatus(SessionStatus.RUNNING);
        when(sessionRepository.findAll()).thenReturn(List.of(session));

        mockMvc.perform(get("/api/sessions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].testScenario").value("ECLAIMS"));
    }

    @Test
    void resultsEndpointReturnsResourceResults() throws Exception {
        UUID sessionId = UUID.randomUUID();
        ResourceResult result = new ResourceResult();
        result.setResourceType("Observation");
        result.setWriteVerifyPassed(false);
        result.setWriteVerifyDiff("Observation.status: missing in SUT response");
        when(sessionRepository.existsById(sessionId)).thenReturn(true);
        when(resultRepository.findByTestSessionId(sessionId)).thenReturn(List.of(result));

        mockMvc.perform(get("/api/sessions/" + sessionId + "/results"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].resourceType").value("Observation"))
            .andExpect(jsonPath("$[0].writeVerifyDiff").value("Observation.status: missing in SUT response"));
    }

    @Test
    void certificateDownloadReturnsPdfBytes() throws Exception {
        UUID sessionId = UUID.randomUUID();
        Path pdf = tempDir.resolve("cert.pdf");
        Files.write(pdf, "%PDF-1.4 cert".getBytes());
        TestSession session = new TestSession();
        session.setId(sessionId);
        session.setCertificatePath(pdf.toString());
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        mockMvc.perform(get("/api/sessions/" + sessionId + "/certificate"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andExpect(content().bytes("%PDF-1.4 cert".getBytes()));
    }

    @Test
    void certificateDownloadReturns404WhenMissing() throws Exception {
        UUID sessionId = UUID.randomUUID();
        TestSession session = new TestSession();
        session.setId(sessionId);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        mockMvc.perform(get("/api/sessions/" + sessionId + "/certificate"))
            .andExpect(status().isNotFound());
    }

    @Test
    void retryDispatchesToExecutionServiceWhenResultBelongsToSession() throws Exception {
        UUID sessionId = UUID.randomUUID();
        TestSession session = new TestSession();
        session.setId(sessionId);
        ResourceResult result = new ResourceResult();
        result.setId(7L);
        result.setTestSession(session);
        when(resultRepository.findById(7L)).thenReturn(Optional.of(result));

        mockMvc.perform(post("/api/sessions/" + sessionId + "/results/7/retry"))
            .andExpect(status().isAccepted());

        verify(testExecutionService).retryResult(7L);
    }

    @Test
    void retryReturns404WhenResultBelongsToDifferentSession() throws Exception {
        UUID sessionId = UUID.randomUUID();
        TestSession otherSession = new TestSession();
        otherSession.setId(UUID.randomUUID());
        ResourceResult result = new ResourceResult();
        result.setId(7L);
        result.setTestSession(otherSession);
        when(resultRepository.findById(7L)).thenReturn(Optional.of(result));

        mockMvc.perform(post("/api/sessions/" + sessionId + "/results/7/retry"))
            .andExpect(status().isNotFound());
    }
}
