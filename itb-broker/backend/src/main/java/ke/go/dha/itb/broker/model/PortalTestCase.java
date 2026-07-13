package ke.go.dha.itb.broker.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import ke.go.dha.itb.broker.model.enums.TestCaseType;

@Entity
@Table(name = "portal_test_cases")
public class PortalTestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "portal_test_scenario_id")
    @JsonIgnore
    private PortalTestScenario portalTestScenario;

    private String resourceType;

    private String endpoint;

    @Enumerated(EnumType.STRING)
    private TestCaseType testType;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public PortalTestScenario getPortalTestScenario() { return portalTestScenario; }
    public void setPortalTestScenario(PortalTestScenario portalTestScenario) { this.portalTestScenario = portalTestScenario; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public TestCaseType getTestType() { return testType; }
    public void setTestType(TestCaseType testType) { this.testType = testType; }
}
