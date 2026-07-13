package ke.go.dha.itb.broker.dto;

import java.util.List;

public record PortalTestRequestPayload(
    String requestId,
    String submittedAt,
    PortalSystemInfo system,
    PortalAuthInfo auth,
    String patientId,
    List<PortalTestScenarioPayload> testScenarios
) {}
