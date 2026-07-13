package ke.go.dha.itb.broker.model;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "portal_test_scenarios")
public class PortalTestScenario {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "portal_test_request_id")
    @JsonIgnore
    private PortalTestRequest portalTestRequest;

    private String scenarioKey;

    @ManyToOne
    @JoinColumn(name = "test_session_id")
    private TestSession testSession;

    @OneToMany(mappedBy = "portalTestScenario", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PortalTestCase> testCases;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public PortalTestRequest getPortalTestRequest() { return portalTestRequest; }
    public void setPortalTestRequest(PortalTestRequest portalTestRequest) { this.portalTestRequest = portalTestRequest; }
    public String getScenarioKey() { return scenarioKey; }
    public void setScenarioKey(String scenarioKey) { this.scenarioKey = scenarioKey; }
    public TestSession getTestSession() { return testSession; }
    public void setTestSession(TestSession testSession) { this.testSession = testSession; }
    public List<PortalTestCase> getTestCases() { return testCases; }
    public void setTestCases(List<PortalTestCase> testCases) { this.testCases = testCases; }
}
