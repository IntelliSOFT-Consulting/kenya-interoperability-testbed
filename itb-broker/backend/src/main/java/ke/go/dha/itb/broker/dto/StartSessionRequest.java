package ke.go.dha.itb.broker.dto;

import java.util.UUID;

public record StartSessionRequest(
    UUID systemConfigId,
    String itbSessionId,
    String itbBaseUrl,
    String testScenario,
    String patientId,
    boolean writeTestEnabled
) {}
