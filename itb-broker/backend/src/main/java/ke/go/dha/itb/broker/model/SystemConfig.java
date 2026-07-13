package ke.go.dha.itb.broker.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import ke.go.dha.itb.broker.model.enums.AuthType;

@Entity
@Table(name = "system_configs")
public class SystemConfig {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String systemName;

    // Nullable: portal-submitted systems (see PortalTestRequest) have a per-resource
    // endpoint instead of one uniform SUT base URL.
    private String sutBaseUrl;

    private String organizationName;

    private String systemVersion;

    @Enumerated(EnumType.STRING)
    private AuthType authType;

    private String authToken;

    private String certificationPortalSystemId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSystemName() { return systemName; }
    public void setSystemName(String systemName) { this.systemName = systemName; }
    public String getSutBaseUrl() { return sutBaseUrl; }
    public void setSutBaseUrl(String sutBaseUrl) { this.sutBaseUrl = sutBaseUrl; }
    public String getOrganizationName() { return organizationName; }
    public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }
    public String getSystemVersion() { return systemVersion; }
    public void setSystemVersion(String systemVersion) { this.systemVersion = systemVersion; }
    public AuthType getAuthType() { return authType; }
    public void setAuthType(AuthType authType) { this.authType = authType; }
    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }
    public String getCertificationPortalSystemId() { return certificationPortalSystemId; }
    public void setCertificationPortalSystemId(String certificationPortalSystemId) { this.certificationPortalSystemId = certificationPortalSystemId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
