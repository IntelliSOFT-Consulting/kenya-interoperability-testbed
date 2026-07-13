package ke.go.dha.itb.broker.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import ke.go.dha.itb.broker.model.enums.PortalRequestStatus;

@Entity
@Table(name = "portal_test_requests")
public class PortalTestRequest {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "system_config_id")
    private SystemConfig systemConfig;

    private String requestId;

    private LocalDateTime submittedAt;

    private String patientId;

    @Enumerated(EnumType.STRING)
    private PortalRequestStatus status;

    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "portalTestRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PortalTestScenario> scenarios;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public SystemConfig getSystemConfig() { return systemConfig; }
    public void setSystemConfig(SystemConfig systemConfig) { this.systemConfig = systemConfig; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }
    public PortalRequestStatus getStatus() { return status; }
    public void setStatus(PortalRequestStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public List<PortalTestScenario> getScenarios() { return scenarios; }
    public void setScenarios(List<PortalTestScenario> scenarios) { this.scenarios = scenarios; }
}
