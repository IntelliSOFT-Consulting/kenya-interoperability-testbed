package ke.go.dha.itb.broker.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import ke.go.dha.itb.broker.model.enums.SessionStatus;

@Entity
@Table(name = "test_sessions")
public class TestSession {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "system_config_id")
    private SystemConfig systemConfig;

    @Column(nullable = false)
    private String itbSessionId;

    @Column(nullable = false)
    private String itbBaseUrl;

    @Column(nullable = false)
    private String testScenario;

    private String patientId;

    private boolean writeTestEnabled;

    @Enumerated(EnumType.STRING)
    private SessionStatus status;

    private String certificatePath;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "testSession", cascade = CascadeType.ALL)
    private List<ResourceResult> results;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public SystemConfig getSystemConfig() { return systemConfig; }
    public void setSystemConfig(SystemConfig systemConfig) { this.systemConfig = systemConfig; }
    public String getItbSessionId() { return itbSessionId; }
    public void setItbSessionId(String itbSessionId) { this.itbSessionId = itbSessionId; }
    public String getItbBaseUrl() { return itbBaseUrl; }
    public void setItbBaseUrl(String itbBaseUrl) { this.itbBaseUrl = itbBaseUrl; }
    public String getTestScenario() { return testScenario; }
    public void setTestScenario(String testScenario) { this.testScenario = testScenario; }
    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }
    public boolean isWriteTestEnabled() { return writeTestEnabled; }
    public void setWriteTestEnabled(boolean writeTestEnabled) { this.writeTestEnabled = writeTestEnabled; }
    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }
    public String getCertificatePath() { return certificatePath; }
    public void setCertificatePath(String certificatePath) { this.certificatePath = certificatePath; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public List<ResourceResult> getResults() { return results; }
    public void setResults(List<ResourceResult> results) { this.results = results; }
}
