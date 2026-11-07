package com.shoppingassistant.dto;

import java.util.List;
import java.util.Map;

import com.shoppingassistant.model.Product;

public record ChatResponse(String sessionId, String message, List<Product> products, Map<String, Object> activeFilters,
                           String recommendedId) {
}
