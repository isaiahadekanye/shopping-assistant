package com.shoppingassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "chat")
public class ChatProperties {
    /** Max model round-trips per shopper message, so a looping model can't run forever. */
    private int maxSteps;

    /** Past exchanges (shopper message + reply) re-sent to OpenAI. */
    private int maxHistoryTurns;

    /** Cards shown when the model searched but didn't pick products itself. */
    private int defaultProductsShown;
}
