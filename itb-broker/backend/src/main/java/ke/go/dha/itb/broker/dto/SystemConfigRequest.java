package ke.go.dha.itb.broker.dto;

import ke.go.dha.itb.broker.model.enums.AuthType;

public record SystemConfigRequest(
    String systemName,
    String sutBaseUrl,
    String organizationName,
    String systemVersion,
    AuthType authType,
    String authToken,
    String certificationPortalSystemId
) {}
