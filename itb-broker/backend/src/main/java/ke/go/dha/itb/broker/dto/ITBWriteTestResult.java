package ke.go.dha.itb.broker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ITBWriteTestResult(
    String status,
    String resourceId,
    boolean stored,
    boolean fieldsMatch,
    String diff,
    String rawResponse
) {}
