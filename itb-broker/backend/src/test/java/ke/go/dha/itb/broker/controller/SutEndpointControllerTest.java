package ke.go.dha.itb.broker.controller;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import ke.go.dha.itb.broker.model.PortalTestCase;
import ke.go.dha.itb.broker.model.PortalTestRequest;
import ke.go.dha.itb.broker.model.PortalTestScenario;
import ke.go.dha.itb.broker.model.SystemConfig;
import ke.go.dha.itb.broker.model.enums.TestCaseType;
import ke.go.dha.itb.broker.repository.PortalTestRequestRepository;
import ke.go.dha.itb.broker.repository.SystemConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SutEndpointController.class)
class SutEndpointControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean SystemConfigRepository systemConfigRepository;
    @MockBean PortalTestRequestRepository portalTestRequestRepository;

    @Test
    void resolvesFromMostRecentPortalTestCase() throws Exception {
        UUID configId = UUID.randomUUID();
        SystemConfig config = new SystemConfig();
        config.setId(configId);
        config.setCertificationPortalSystemId("sys-00123");
        when(systemConfigRepository.findByCertificationPortalSystemId("sys-00123"))
            .thenReturn(Optional.of(config));

        PortalTestCase testCase = new PortalTestCase();
        testCase.setResourceType("Patient");
        testCase.setEndpoint("https://emr.example.ke/fhir/patDemo");
        testCase.setTestType(TestCaseType.READ);
        PortalTestScenario scenario = new PortalTestScenario();
        scenario.setTestCases(List.of(testCase));
        PortalTestRequest request = new PortalTestRequest();
        request.setScenarios(List.of(scenario));
        when(portalTestRequestRepository.findBySystemConfigIdOrderByCreatedAtDesc(configId))
            .thenReturn(List.of(request));

        mockMvc.perform(get("/api/sut-endpoints/sys-00123/Patient"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.endpoint").value("https://emr.example.ke/fhir/patDemo"));
    }

    @Test
    void resourceTypeMatchIsCaseInsensitive() throws Exception {
        UUID configId = UUID.randomUUID();
        SystemConfig config = new SystemConfig();
        config.setId(configId);
        when(systemConfigRepository.findByCertificationPortalSystemId("sys-00123"))
            .thenReturn(Optional.of(config));

        PortalTestCase testCase = new PortalTestCase();
        testCase.setResourceType("Patient");
        testCase.setEndpoint("https://emr.example.ke/fhir/patDemo");
        testCase.setTestType(TestCaseType.READ);
        PortalTestScenario scenario = new PortalTestScenario();
        scenario.setTestCases(List.of(testCase));
        PortalTestRequest request = new PortalTestRequest();
        request.setScenarios(List.of(scenario));
        when(portalTestRequestRepository.findBySystemConfigIdOrderByCreatedAtDesc(configId))
            .thenReturn(List.of(request));

        mockMvc.perform(get("/api/sut-endpoints/sys-00123/patient"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.endpoint").value("https://emr.example.ke/fhir/patDemo"));
    }

    @Test
    void fallsBackToSutBaseUrlWhenNoPortalTestCaseMatches() throws Exception {
        UUID configId = UUID.randomUUID();
        SystemConfig config = new SystemConfig();
        config.setId(configId);
        config.setSutBaseUrl("https://emr.example.ke/fhir");
        when(systemConfigRepository.findByCertificationPortalSystemId("sys-00123"))
            .thenReturn(Optional.of(config));
        when(portalTestRequestRepository.findBySystemConfigIdOrderByCreatedAtDesc(configId))
            .thenReturn(List.of());

        mockMvc.perform(get("/api/sut-endpoints/sys-00123/Condition"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.endpoint").value("https://emr.example.ke/fhir/Condition"));
    }

    @Test
    void returns404WhenSystemUnknown() throws Exception {
        when(systemConfigRepository.findByCertificationPortalSystemId("sys-nope"))
            .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/sut-endpoints/sys-nope/Patient"))
            .andExpect(status().isNotFound());
    }

    @Test
    void returns404WhenNoEndpointResolvable() throws Exception {
        UUID configId = UUID.randomUUID();
        SystemConfig config = new SystemConfig();
        config.setId(configId);
        when(systemConfigRepository.findByCertificationPortalSystemId("sys-00123"))
            .thenReturn(Optional.of(config));
        when(portalTestRequestRepository.findBySystemConfigIdOrderByCreatedAtDesc(configId))
            .thenReturn(List.of());

        mockMvc.perform(get("/api/sut-endpoints/sys-00123/Patient"))
            .andExpect(status().isNotFound());
    }
}
