package ke.go.dha.itb.broker.dto;

public record PortalSystemInfo(
    String name,
    String organizationName,
    String version,
    String certificationPortalSystemId
) {}
