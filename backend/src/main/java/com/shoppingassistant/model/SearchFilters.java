package com.shoppingassistant.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** Arguments of the search_products tool. */
public record SearchFilters(String query, String category, String color, Double minPrice, Double maxPrice, String sort) {

    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (query != null) map.put("query", query);
        if (category != null) map.put("category", category);
        if (color != null) map.put("color", color);
        if (minPrice != null) map.put("min_price", minPrice);
        if (maxPrice != null) map.put("max_price", maxPrice);
        if (sort != null && !sort.equals("relevance")) map.put("sort", sort);
        return map;
    }
}
