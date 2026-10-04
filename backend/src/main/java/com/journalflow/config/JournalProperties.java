package com.journalflow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "journal")
public class JournalProperties {
    private Jwt jwt = new Jwt();
    private String uploadDir = "./uploads";
    private Llm llm = new Llm();

    @Data
    public static class Jwt {
        private String secret;
        private long expireHours = 72;
    }

    @Data
    public static class Llm {
        private String provider = "mock";
        private String baseUrl;
        private String apiKey;
        private String model;
        private int timeoutSeconds = 30;
        private double temperature = 0.2;
        private int maxTokens = 1024;
    }
}
