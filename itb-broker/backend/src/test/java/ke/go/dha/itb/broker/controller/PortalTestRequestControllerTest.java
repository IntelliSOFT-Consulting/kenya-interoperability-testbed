package ke.go.dha.itb.broker.controller;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import ke.go.dha.itb.broker.model.PortalTestCase;
import ke.go.dha.itb.broker.model.PortalTestRequest;
import ke.go.dha.itb.broker.model.PortalTestScenario;
import ke.go.dha.itb.broker.model.SystemConfig;
import ke.go.dha.itb.broker.model.enums.PortalRequestStatus;
import ke.go.dha.itb.broker.model.enums.TestCaseType;
import ke.go.dha.itb.broker.repository.PortalTestRequestRepository;
import ke.go.dha.itb.broker.repository.SystemConfigRepository;
import ke.go.dha.itb.broker.repository.TestSessionRepository;
import ke.go.dha.itb.broker.service.PortalCallbackService;
import ke.go.dha.itb.broker.service.TestExecutionService;
import org.junit.jupiter.api.Test;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PortalTestRequestController.class)
class PortalTestRequestControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean PortalTestRequestRepository requestRepository;
    @MockBean SystemConfigRepository systemConfigRepository;
    @MockBean TestSessionRepository sessionRepository;
    @MockBean TestExecutionService testExecutionService;
    @MockBean PortalCallbackService portalCallbackService;

    @Test
    void receiveUpsertsSystemAndStoresRequestAsPending() throws Exception {
        when(systemConfigRepository.findByCertificationPortalSystemId("sys-00123"))
            .thenReturn(Optional.empty());
        when(systemConfigRepository.save(any())).thenAnswer(inv -> {
            SystemConfig c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(requestRepository.save(any())).thenAnswer(inv -> {
            PortalTestRequest r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        mockMvc.perform(post("/api/portal/test-requests")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "requestId": "req-1",
                      "system": {
                        "name": "Aga Khan EMR v2.1",
                        "organizationName": "Aga Khan University Hospital",
                        "version": "2.1.0",
                        "certificationPortalSystemId": "sys-00123"
                      },
                      "auth": { "type": "BEARER", "token": "tkn" },
                      "patientId": "12345",
                      "testScenarios": [
                        {
                          "scenarioKey": "PATIENT_SUMMARY",
                          "testCases": [
                            { "resourceType": "Patient", "endpoint": "https://emr.example.ke/fhir/patDemo", "testType": "READ" },
                            { "resourceType": "Observation", "endpoint": "https://emr.example.ke/fhir/obs", "testType": "WRITE" }
                          ]
                        }
                      ]
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.scenarios[0].scenarioKey").value("PATIENT_SUMMARY"))
            .andExpect(jsonPath("$.scenarios[0].testCases[0].resourceType").value("Patient"))
            .andExpect(jsonPath("$.scenarios[0].testCases[1].testType").value("WRITE"));
    }

    @Test
    void startCreatesSessionsPerScenarioAndMarksStarted() throws Exception {
        UUID requestId = UUID.randomUUID();
        SystemConfig config = new SystemConfig();
        config.setId(UUID.randomUUID());

        PortalTestCase testCase = new PortalTestCase();
        testCase.setResourceType("Patient");
        testCase.setEndpoint("https://emr.example.ke/fhir/patDemo");
        testCase.setTestType(TestCaseType.READ);

        PortalTestScenario scenario = new PortalTestScenario();
        scenario.setScenarioKey("PATIENT_SUMMARY");
        scenario.setTestCases(List.of(testCase));

        PortalTestRequest request = new PortalTestRequest();
        request.setId(requestId);
        request.setSystemConfig(config);
        request.setStatus(PortalRequestStatus.PENDING);
        request.setScenarios(List.of(scenario));

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(sessionRepository.save(any())).thenAnswer(inv -> {
            var s = inv.getArgument(0, ke.go.dha.itb.broker.model.TestSession.class);
            s.setId(UUID.randomUUID());
            return s;
        });
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/portal/test-requests/" + requestId + "/start")
                .contentType(APPLICATION_JSON)
                .content("""
                    { "itbSessionId": "78595BBDX5F0FX452DX8A34XE2FF49184EB3", "itbBaseUrl": "http://itb-srv:8080" }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("STARTED"));

        verify(testExecutionService).executePortalScenario(any(), any());
    }

    @Test
    void startRejectsAlreadyStartedRequest() throws Exception {
        UUID requestId = UUID.randomUUID();
        PortalTestRequest request = new PortalTestRequest();
        request.setId(requestId);
        request.setStatus(PortalRequestStatus.STARTED);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));

        mockMvc.perform(post("/api/portal/test-requests/" + requestId + "/start")
                .contentType(APPLICATION_JSON)
                .content("{ \"itbSessionId\": \"X\", \"itbBaseUrl\": \"http://itb-srv:8080\" }"))
            .andExpect(status().isConflict());
    }

    @Test
    void listReturnsAllRequests() throws Exception {
        when(requestRepository.findAll()).thenReturn(List.of(new PortalTestRequest()));

        mockMvc.perform(get("/api/portal/test-requests"))
            .andExpect(status().isOk());
    }

    @Test
    void sendStatusDelegatesToPortalCallbackService() throws Exception {
        UUID requestId = UUID.randomUUID();
        PortalTestRequest request = new PortalTestRequest();
        request.setId(requestId);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));

        mockMvc.perform(post("/api/portal/test-requests/" + requestId + "/send-status"))
            .andExpect(status().isNoContent());

        verify(portalCallbackService).sendStatus(request);
    }
}
