package com.shoppingassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "catalog")
public class CatalogProperties {
    private String jsonPath;
    private int batchSize;
    private int searchLimit;
    /** Matches fetched before duplicate listings (same title) are removed; at least search-limit. */
    private int searchCandidates;
}
