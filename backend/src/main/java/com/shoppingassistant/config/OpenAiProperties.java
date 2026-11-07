package com.shoppingassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;
import lombok.ToString;

@Data
@ConfigurationProperties(prefix = "openai")
public class OpenAiProperties {
    private String baseUrl;

    @ToString.Exclude
    private String apiKey;

    private String model;
    /** Sent as reasoning_effort when set; leave blank for models that don't support it. */
    private String reasoningEffort;
    private int timeoutSeconds;
}
