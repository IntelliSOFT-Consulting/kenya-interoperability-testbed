package ke.go.dha.itb.broker.dto;

import java.util.List;

public record PortalTestScenarioPayload(
    String scenarioKey,
    List<PortalTestCasePayload> testCases
) {}
