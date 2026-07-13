package ke.go.dha.itb.broker.dto;

public record StartPortalRequestBody(
    String itbSessionId,
    String itbBaseUrl
) {}
