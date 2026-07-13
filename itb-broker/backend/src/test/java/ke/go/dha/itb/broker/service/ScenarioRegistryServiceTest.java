package ke.go.dha.itb.broker.service;

import java.io.IOException;

import ke.go.dha.itb.broker.dto.ScenarioDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScenarioRegistryServiceTest {

    private ScenarioRegistryService registry;

    @BeforeEach
    void setUp() throws IOException {
        registry = new ScenarioRegistryService();
        registry.load();
    }

    @Test
    void loadsAllFourScenarios() {
        assertThat(registry.all()).hasSize(4);
        assertThat(registry.all())
            .extracting(ScenarioDefinition::getScenarioKey)
            .containsExactlyInAnyOrder("PATIENT_SUMMARY", "ECLAIMS", "LAB", "IMMUNIZATION");
    }

    @Test
    void patientSummaryHasSpecResources() {
        ScenarioDefinition def = registry.get("PATIENT_SUMMARY");
        assertThat(def.getLabel()).isEqualTo("Patient Summary (IPS)");
        assertThat(def.getReadResources()).containsExactly(
            "Patient", "Condition", "AllergyIntolerance", "MedicationStatement",
            "Observation", "Immunization", "DiagnosticReport");
        assertThat(def.getWriteResources()).containsExactly("Patient", "Observation");
    }

    @Test
    void eclaimsHasSpecResources() {
        ScenarioDefinition def = registry.get("ECLAIMS");
        assertThat(def.getLabel()).isEqualTo("eClaims");
        assertThat(def.getReadResources()).containsExactly(
            "Patient", "Claim", "ClaimResponse", "Coverage", "Organization", "Practitioner");
        assertThat(def.getWriteResources()).containsExactly("Claim");
    }

    @Test
    void labHasSpecResources() {
        ScenarioDefinition def = registry.get("LAB");
        assertThat(def.getLabel()).isEqualTo("Laboratory");
        assertThat(def.getReadResources()).containsExactly(
            "Patient", "ServiceRequest", "DiagnosticReport", "Observation", "Specimen");
        assertThat(def.getWriteResources()).containsExactly("ServiceRequest", "DiagnosticReport");
    }

    @Test
    void immunizationHasSpecResources() {
        ScenarioDefinition def = registry.get("IMMUNIZATION");
        assertThat(def.getLabel()).isEqualTo("Immunization");
        assertThat(def.getReadResources()).containsExactly(
            "Patient", "Immunization", "ImmunizationRecommendation");
        assertThat(def.getWriteResources()).containsExactly("Immunization");
    }

    @Test
    void unknownScenarioThrows() {
        assertThatThrownBy(() -> registry.get("UNKNOWN"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown scenario: UNKNOWN");
    }
}
