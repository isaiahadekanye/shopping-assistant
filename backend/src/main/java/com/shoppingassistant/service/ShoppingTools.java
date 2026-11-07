package com.shoppingassistant.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shoppingassistant.config.ChatProperties;
import com.shoppingassistant.model.ChatSession;
import com.shoppingassistant.model.Product;
import com.shoppingassistant.model.SearchFilters;

/**
 * The tools the model can call. Their schemas ({@link #DEFINITIONS}) are sent to OpenAI on every request
 * by ChatService, and {@link #execute} runs them.
 */
@Component
public class ShoppingTools {

    // ---- Available tools -------------------------------------------------------------------
    /** Search the catalog by keywords, category, colour and price. */
    public static final String SEARCH_PRODUCTS = "search_products";
    /** Choose which search results are shown to the shopper as cards. */
    public static final String SHOW_PRODUCTS = "show_products";

    /** OpenAI tool schemas (JSON). */
    private static final String DEFINITIONS = """
            [
              {"type": "function", "function": {
                "name": "%s",
                "description": "Search the catalog. Returns matching products with their real prices, details and category.",
                "parameters": {"type": "object", "required": ["query"], "properties": {
                  "query": {"type": "string", "description": "1-4 product keywords, e.g. 'women running shoes'. Every word must match, so no filler words, prices or colours."},
                  "category": {"type": "string", "description": "Product type that must appear in the store category, e.g. 'running shoes', 'shorts', 'dress', 'backpack'. Use it for product requests so accessories don't slip in; leave it out for brand-only or vague searches."},
                  "color": {"type": "string", "description": "Colour word, e.g. 'blue'"},
                  "min_price": {"type": "number"},
                  "max_price": {"type": "number"},
                  "sort": {"type": "string", "enum": ["relevance", "price_low_to_high", "price_high_to_low", "rating"]}
                }}}},
              {"type": "function", "function": {
                "name": "%s",
                "description": "Display product cards to the shopper. Only ids returned by search_products are allowed.",
                "parameters": {"type": "object", "required": ["ids"], "properties": {
                  "ids": {"type": "array", "items": {"type": "string"}, "description": "Best first, usually 3-6"},
                  "recommended": {"type": "string", "description": "Optional: the id of your single top pick, only when you recommend one product above the others. Must be one of ids."}
                }}}}
            ]""".formatted(SEARCH_PRODUCTS, SHOW_PRODUCTS);

    private final ProductService productService;
    private final ObjectMapper mapper;
    private final ChatProperties chat;
    private final JsonNode definitions;

    public ShoppingTools(ProductService productService, ObjectMapper mapper, ChatProperties chat) throws JsonProcessingException {
        this.productService = productService;
        this.mapper = mapper;
        this.chat = chat;
        this.definitions = mapper.readTree(DEFINITIONS);
    }

    public JsonNode definitions() {
        return definitions;
    }

    /** Runs one tool call and returns the JSON result sent back to the model. */
    public String execute(String name, JsonNode args, ChatSession session) {
        return switch (name) {
            case SEARCH_PRODUCTS -> search(args, session);
            case SHOW_PRODUCTS -> show(args, session);
            default -> "{\"error\": \"Unknown tool " + name + "\"}";
        };
    }

    private String search(JsonNode args, ChatSession session) {
        SearchFilters filters = new SearchFilters(text(args, "query"), text(args, "category"), text(args, "color"),
                number(args, "min_price"), number(args, "max_price"), text(args, "sort"));
        List<Product> products = productService.search(filters);

        session.setActiveFilters(filters.asMap());
        // Show the top results by default; show_products replaces them with the model's choice.
        session.setShownIds(products.stream().map(Product::getId).limit(chat.getDefaultProductsShown()).toList());
        session.setRecommendedId(null);
        products.forEach(p -> session.getSeenIds().add(p.getId()));

        ObjectNode result = mapper.createObjectNode();
        ArrayNode list = result.putArray("results");
        for (Product p : products) {
            list.addObject()
                    .put("id", p.getId()).put("title", p.getTitle()).put("brand", p.getBrand()).put("category", p.getCategoryPath())
                    .put("price", p.getPrice()).put("rating", p.getRating()).put("rating_count", p.getRatingCount())
                    .put("listed_color", p.getColor()).put("listed_size", p.getSize()).put("material", p.getMaterial())
                    .putPOJO("features", p.getFeatures().stream().limit(3).toList());
        }
        if (products.isEmpty()) {
            result.put("note", "No products match. Tell the shopper, then offer to broaden the query or drop a filter.");
        }
        return result.toString();
    }

    private String show(JsonNode args, ChatSession session) {
        List<String> accepted = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        args.path("ids").forEach(id -> (session.getSeenIds().contains(id.asText()) ? accepted : rejected).add(id.asText()));
        session.setShownIds(accepted);
        String recommended = text(args, "recommended");
        // A top pick only makes sense when there are others to hide behind it.
        session.setRecommendedId(accepted.size() > 1 && accepted.contains(recommended) ? recommended : null);

        ObjectNode result = mapper.createObjectNode().put("shown", accepted.size());
        if (!rejected.isEmpty()) {
            result.put("error", "These ids were not returned by search_products and were not shown: " + rejected);
        }
        return result.toString();
    }

    private static String text(JsonNode args, String field) {
        String s = args.path(field).asText("").trim();
        return s.isEmpty() ? null : s;
    }

    private static Double number(JsonNode args, String field) {
        return args.path(field).isNumber() ? args.path(field).asDouble() : null;
    }
}
