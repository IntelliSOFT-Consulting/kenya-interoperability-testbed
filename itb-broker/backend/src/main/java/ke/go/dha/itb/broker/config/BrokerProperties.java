package ke.go.dha.itb.broker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "broker")
public class BrokerProperties {

    private final Itb itb = new Itb();
    private final Portal portal = new Portal();
    private final Storage storage = new Storage();
    private final Async async = new Async();

    public Itb getItb() { return itb; }
    public Portal getPortal() { return portal; }
    public Storage getStorage() { return storage; }
    public Async getAsync() { return async; }

    public static class Itb {
        private String baseUrl;
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    public static class Portal {
        private String baseUrl;
        private String apiKey;
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }

    public static class Storage {
        private String certificatePath;
        public String getCertificatePath() { return certificatePath; }
        public void setCertificatePath(String certificatePath) { this.certificatePath = certificatePath; }
    }

    public static class Async {
        private int corePoolSize = 5;
        private int maxPoolSize = 20;
        public int getCorePoolSize() { return corePoolSize; }
        public void setCorePoolSize(int corePoolSize) { this.corePoolSize = corePoolSize; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
    }
}
