package ke.go.dha.itb.broker.controller;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import ke.go.dha.itb.broker.model.SystemConfig;
import ke.go.dha.itb.broker.model.enums.AuthType;
import ke.go.dha.itb.broker.repository.SystemConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@WebMvcTest(SystemConfigController.class)
class SystemConfigControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean SystemConfigRepository repository;

    private SystemConfig config(UUID id) {
        SystemConfig config = new SystemConfig();
        config.setId(id);
        config.setSystemName("Aga Khan EMR v2.1");
        config.setSutBaseUrl("https://emr.agakhan.co.ke/fhir");
        config.setOrganizationName("Aga Khan University Hospital");
        config.setSystemVersion("2.1.0");
        config.setAuthType(AuthType.BEARER);
        config.setAuthToken("tkn");
        config.setCertificationPortalSystemId("sys-00123");
        return config;
    }

    @Test
    void createReturns201WithSavedConfig() throws Exception {
        UUID id = UUID.randomUUID();
        when(repository.save(any())).thenAnswer(inv -> {
            SystemConfig c = inv.getArgument(0);
            c.setId(id);
            return c;
        });

        mockMvc.perform(post("/api/systems")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "systemName": "Aga Khan EMR v2.1",
                      "sutBaseUrl": "https://emr.agakhan.co.ke/fhir",
                      "organizationName": "Aga Khan University Hospital",
                      "systemVersion": "2.1.0",
                      "authType": "BEARER",
                      "authToken": "tkn",
                      "certificationPortalSystemId": "sys-00123"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.systemName").value("Aga Khan EMR v2.1"))
            .andExpect(jsonPath("$.organizationName").value("Aga Khan University Hospital"))
            .andExpect(jsonPath("$.systemVersion").value("2.1.0"))
            .andExpect(jsonPath("$.authType").value("BEARER"));
    }

    @Test
    void listReturnsAllSystems() throws Exception {
        when(repository.findAll()).thenReturn(List.of(config(UUID.randomUUID())));

        mockMvc.perform(get("/api/systems"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].systemName").value("Aga Khan EMR v2.1"));
    }

    @Test
    void getReturnsSystemOr404() throws Exception {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(config(id)));

        mockMvc.perform(get("/api/systems/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()));

        mockMvc.perform(get("/api/systems/" + UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void updateModifiesExistingConfig() throws Exception {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(config(id)));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/systems/" + id)
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "systemName": "Aga Khan EMR v2.2",
                      "sutBaseUrl": "https://emr.agakhan.co.ke/fhir",
                      "organizationName": "Aga Khan University Hospital",
                      "systemVersion": "2.2.0",
                      "authType": "BEARER",
                      "authToken": "refreshed-token",
                      "certificationPortalSystemId": "sys-00123"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.systemName").value("Aga Khan EMR v2.2"))
            .andExpect(jsonPath("$.authToken").value("refreshed-token"));
    }

    @Test
    void deleteRemovesConfig() throws Exception {
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(true);

        mockMvc.perform(delete("/api/systems/" + id))
            .andExpect(status().isNoContent());
        verify(repository).deleteById(id);
    }
}
