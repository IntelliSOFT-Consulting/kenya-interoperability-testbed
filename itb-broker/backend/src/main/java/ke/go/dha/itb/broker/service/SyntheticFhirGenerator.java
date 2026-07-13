package ke.go.dha.itb.broker.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

@Service
public class SyntheticFhirGenerator {

    public String generate(String resourceType) {
        try {
            ClassPathResource r = new ClassPathResource(
                "synthetic/" + resourceType + ".json");
            return StreamUtils.copyToString(
                r.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                "No synthetic template for: " + resourceType, e);
        }
    }
}
