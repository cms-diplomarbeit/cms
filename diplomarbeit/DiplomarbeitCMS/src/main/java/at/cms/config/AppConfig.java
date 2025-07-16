package at.cms.config;

public class AppConfig {
    private static final String DEFAULT_QDRANT_URL = "http://localhost:6333";
    private static final String DEFAULT_OLLAMA_URL = "http://file1.lan.elite-zettl.at:11434";
    private static final String DEFAULT_TIKA_URL = "http://dev1.lan.elite-zettl.at:9998";
    private static final String DEFAULT_EMBEDDING_URL = "http://file1.lan.elite-zettl.at:11434";
    
    public enum ServiceMode {
        DATA_PROCESSOR,
        VECTORIZER,
        FULL  // Default mode - runs both services
    }
    
    public static String getQdrantUrl() {
        return System.getenv().getOrDefault("QDRANT_URL", DEFAULT_QDRANT_URL);
    }
    
    public static String getOllamaUrl() {
        return System.getenv().getOrDefault("OLLAMA_URL", DEFAULT_OLLAMA_URL);
    }
    
    public static String getTikaUrl() {
        return System.getenv().getOrDefault("TIKA_URL", DEFAULT_TIKA_URL);
    }

    public static String getEmbeddingUrl() {
        return System.getenv().getOrDefault("EMBEDDING_URL", DEFAULT_EMBEDDING_URL);
    }
    
    public static String getWatchDir() {
        return System.getenv().getOrDefault("WATCH_DIR", "./watched");
    }
    
    public static ServiceMode getServiceMode() {
        String mode = System.getenv("SERVICE_MODE");
        if (mode == null) return ServiceMode.FULL;
        
        try {
            return ServiceMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ServiceMode.FULL;
        }
    }
    
    public static boolean isDockerEnvironment() {
        return System.getenv("DOCKER_ENV") != null;
    }
}
