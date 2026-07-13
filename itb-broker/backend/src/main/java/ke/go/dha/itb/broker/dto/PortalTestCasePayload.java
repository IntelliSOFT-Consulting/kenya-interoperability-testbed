package ke.go.dha.itb.broker.dto;

public record PortalTestCasePayload(
    String resourceType,
    String endpoint,
    String testType
) {}
