package ke.go.dha.itb.broker.dto;

import java.util.List;

public class ScenarioDefinition {

    private String scenarioKey;
    private String label;
    private List<String> readResources;
    private List<String> writeResources;

    public String getScenarioKey() { return scenarioKey; }
    public void setScenarioKey(String scenarioKey) { this.scenarioKey = scenarioKey; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public List<String> getReadResources() { return readResources; }
    public void setReadResources(List<String> readResources) { this.readResources = readResources; }
    public List<String> getWriteResources() { return writeResources; }
    public void setWriteResources(List<String> writeResources) { this.writeResources = writeResources; }
}
