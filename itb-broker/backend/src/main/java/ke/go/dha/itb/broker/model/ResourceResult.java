package ke.go.dha.itb.broker.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
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
@Table(name = "resource_results")
public class ResourceResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "test_session_id")
    @JsonIgnore
    private TestSession testSession;

    private String resourceType;

    @Enumerated(EnumType.STRING)
    private TestCaseType testType;

    @Column(columnDefinition = "TEXT")
    private String sutEndpoint;

    @Column(columnDefinition = "TEXT")
    private String itbEndpoint;

    private String fetchStatus;

    @Column(columnDefinition = "TEXT")
    private String fetchedPayload;

    private String itbPostStatus;

    @Column(columnDefinition = "TEXT")
    private String itbResponse;

    private String writeTestStatus;

    @Column(columnDefinition = "TEXT")
    private String writeTestResponse;

    private Boolean writeVerifyPassed;

    @Column(columnDefinition = "TEXT")
    private String writeVerifyDiff;

    private LocalDateTime testedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public TestSession getTestSession() { return testSession; }
    public void setTestSession(TestSession testSession) { this.testSession = testSession; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public TestCaseType getTestType() { return testType; }
    public void setTestType(TestCaseType testType) { this.testType = testType; }
    public String getSutEndpoint() { return sutEndpoint; }
    public void setSutEndpoint(String sutEndpoint) { this.sutEndpoint = sutEndpoint; }
    public String getItbEndpoint() { return itbEndpoint; }
    public void setItbEndpoint(String itbEndpoint) { this.itbEndpoint = itbEndpoint; }
    public String getFetchStatus() { return fetchStatus; }
    public void setFetchStatus(String fetchStatus) { this.fetchStatus = fetchStatus; }
    public String getFetchedPayload() { return fetchedPayload; }
    public void setFetchedPayload(String fetchedPayload) { this.fetchedPayload = fetchedPayload; }
    public String getItbPostStatus() { return itbPostStatus; }
    public void setItbPostStatus(String itbPostStatus) { this.itbPostStatus = itbPostStatus; }
    public String getItbResponse() { return itbResponse; }
    public void setItbResponse(String itbResponse) { this.itbResponse = itbResponse; }
    public String getWriteTestStatus() { return writeTestStatus; }
    public void setWriteTestStatus(String writeTestStatus) { this.writeTestStatus = writeTestStatus; }
    public String getWriteTestResponse() { return writeTestResponse; }
    public void setWriteTestResponse(String writeTestResponse) { this.writeTestResponse = writeTestResponse; }
    public Boolean getWriteVerifyPassed() { return writeVerifyPassed; }
    public void setWriteVerifyPassed(Boolean writeVerifyPassed) { this.writeVerifyPassed = writeVerifyPassed; }
    public String getWriteVerifyDiff() { return writeVerifyDiff; }
    public void setWriteVerifyDiff(String writeVerifyDiff) { this.writeVerifyDiff = writeVerifyDiff; }
    public LocalDateTime getTestedAt() { return testedAt; }
    public void setTestedAt(LocalDateTime testedAt) { this.testedAt = testedAt; }
}
