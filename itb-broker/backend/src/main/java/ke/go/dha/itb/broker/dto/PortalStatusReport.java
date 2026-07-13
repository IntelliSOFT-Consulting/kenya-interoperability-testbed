package ke.go.dha.itb.broker.dto;

import java.util.List;

public record PortalStatusReport(
    String requestId,
    String organizationName,
    String systemName,
    String systemVersion,
    String certificationPortalSystemId,
    List<ScenarioStatus> scenarios
) {
    public record ScenarioStatus(
        String scenarioKey,
        String status,
        String itbSessionId,
        long readPassed,
        long readTotal,
        long writePassed,
        long writeTotal,
        boolean certificateAvailable
    ) {}
}
