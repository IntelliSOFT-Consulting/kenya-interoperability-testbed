package ke.go.dha.itb.broker.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import ke.go.dha.itb.broker.model.PortalTestCase;
import ke.go.dha.itb.broker.model.PortalTestRequest;
import ke.go.dha.itb.broker.model.PortalTestScenario;
import ke.go.dha.itb.broker.model.SystemConfig;
import ke.go.dha.itb.broker.repository.PortalTestRequestRepository;
import ke.go.dha.itb.broker.repository.SystemConfigRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Called by ITB TDL test cases at run time — not by the broker's own frontend —
// so a test case can forward a write to the real SUT endpoint instead of each
// TDL file hardcoding one fixed target server. Keyed by certificationPortalSystemId
// because that's the one identifier the broker can embed as a query parameter on
// its own POSTs to ITB (see ITBClient); the ITB session id itself isn't readable
// from within a TDL script.
@RestController
@RequestMapping("/api/sut-endpoints")
public class SutEndpointController {

    private final SystemConfigRepository systemConfigRepository;
    private final PortalTestRequestRepository portalTestRequestRepository;

    public SutEndpointController(
        SystemConfigRepository systemConfigRepository,
        PortalTestRequestRepository portalTestRequestRepository
    ) {
        this.systemConfigRepository = systemConfigRepository;
        this.portalTestRequestRepository = portalTestRequestRepository;
    }

    @GetMapping("/{certificationPortalSystemId}/{resourceType}")
    public ResponseEntity<Map<String, String>> resolve(
        @PathVariable String certificationPortalSystemId,
        @PathVariable String resourceType
    ) {
        SystemConfig config = systemConfigRepository
            .findByCertificationPortalSystemId(certificationPortalSystemId)
            .orElse(null);
        if (config == null) {
            return ResponseEntity.notFound().build();
        }

        String endpoint = latestPortalEndpoint(config.getId(), resourceType);
        if (endpoint == null && config.getSutBaseUrl() != null) {
            endpoint = config.getSutBaseUrl() + "/" + resourceType;
        }
        if (endpoint == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("endpoint", endpoint));
    }

    // Most recent portal-submitted testcase for this resource type wins, since a
    // system can have several requests over time with different endpoints.
    private String latestPortalEndpoint(UUID systemConfigId, String resourceType) {
        List<PortalTestRequest> requests =
            portalTestRequestRepository.findBySystemConfigIdOrderByCreatedAtDesc(systemConfigId);
        for (PortalTestRequest request : requests) {
            for (PortalTestScenario scenario : request.getScenarios()) {
                for (PortalTestCase testCase : scenario.getTestCases()) {
                    if (testCase.getResourceType().equalsIgnoreCase(resourceType)) {
                        return testCase.getEndpoint();
                    }
                }
            }
        }
        return null;
    }
}
