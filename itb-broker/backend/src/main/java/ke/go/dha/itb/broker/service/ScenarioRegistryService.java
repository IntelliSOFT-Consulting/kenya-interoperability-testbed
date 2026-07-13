package ke.go.dha.itb.broker.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import jakarta.annotation.PostConstruct;
import ke.go.dha.itb.broker.dto.ScenarioDefinition;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

@Service
public class ScenarioRegistryService {

    private final Map<String, ScenarioDefinition> registry = new HashMap<>();

    @PostConstruct
    public void load() throws IOException {
        YAMLMapper mapper = new YAMLMapper();
        PathMatchingResourcePatternResolver resolver =
            new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:scenarios/*.yaml");
        for (Resource r : resources) {
            ScenarioDefinition def = mapper.readValue(
                r.getInputStream(), ScenarioDefinition.class);
            registry.put(def.getScenarioKey(), def);
        }
    }

    public ScenarioDefinition get(String scenarioKey) {
        ScenarioDefinition def = registry.get(scenarioKey);
        if (def == null) throw new IllegalArgumentException(
            "Unknown scenario: " + scenarioKey);
        return def;
    }

    public List<ScenarioDefinition> all() {
        return new ArrayList<>(registry.values());
    }
}
