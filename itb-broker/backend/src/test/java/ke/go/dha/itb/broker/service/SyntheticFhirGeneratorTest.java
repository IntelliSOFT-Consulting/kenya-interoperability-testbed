package ke.go.dha.itb.broker.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyntheticFhirGeneratorTest {

    private final SyntheticFhirGenerator generator = new SyntheticFhirGenerator();

    @ParameterizedTest
    @ValueSource(strings = {
        "Patient", "Condition", "Observation", "Claim", "ServiceRequest",
        "DiagnosticReport", "Immunization", "AllergyIntolerance",
        "MedicationStatement", "Coverage"
    })
    void loadsTemplateForEveryRequiredResourceType(String resourceType) {
        String json = generator.generate(resourceType);
        assertThat(json).contains("\"resourceType\": \"" + resourceType + "\"");
    }

    @Test
    void unknownResourceTypeThrows() {
        assertThatThrownBy(() -> generator.generate("Bogus"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No synthetic template for: Bogus");
    }
}
