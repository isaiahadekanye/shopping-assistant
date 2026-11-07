package com.shoppingassistant.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shoppingassistant.config.ChatProperties;
import com.shoppingassistant.dto.ChatResponse;
import com.shoppingassistant.llm.OpenAiClient;
import com.shoppingassistant.model.ChatSession;
import com.shoppingassistant.model.Product;
import com.shoppingassistant.repository.ChatSessionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The chat agent: sends the conversation to OpenAI, runs the tools it asks for, and repeats
 * until it replies in text. Product cards always come from the database, never from model text.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {
    static final String GAVE_UP = "Sorry, I couldn't finish that search. Could you rephrase it?";

    private static final String SYSTEM_PROMPT = """
            You are a shopping assistant for an online fashion store (clothing, shoes, jewelry, bags, accessories).

            Rules:
            - Only recommend products returned by search_products. Never invent products, prices, sizes,
              colours, materials or other details. If a value is null, say it isn't listed.
            - Each listing has ONE listed colour and size and there is no stock data, so never promise
              other sizes, colours or availability.
            - For any product request, call search_products, then call show_products with the ids you recommend.
              When showing 2 or more products and one is the best fit, also pass its id as recommended. The shopper then
              sees only that card, with a button to see the rest, so describe the top pick and say how many other
              options they can view (don't say the cards show them).
              Search is keyword-based, so check each result's title and category and skip ones that don't fit
              (e.g. shoelaces when asked for running shoes). Set category to the product type to avoid that;
              if a category gives no results, retry without it.
            - Follow-ups refine the last search: keep its filters, with the same values, unless the shopper
              changes them. But if the shopper asks for a different kind of product (e.g. backpacks, then shirts),
              start a new search: drop colour, price and sort unless the shopper says them again.
              "Cheaper" = set max_price below the cheapest product currently shown, or sort price_low_to_high.
              "Only blue" = add color "blue". When you mention a filter, state the value actually used, and don't
              call a limit you chose yourself the shopper's limit.
              When the shopper removes a filter, drop it completely, including its words in query: removing
              category "boots" from query "hiking boots" means searching "hiking" with no category.
            - If nothing matches, say so plainly and suggest the closest alternative.
            - Clarify first: if the request doesn't name a product type (a gift for someone with a hobby, e.g.
              "someone who loves hiking", or "something nice"), reply with ONE short question offering 2-3 product
              types and do NOT call any tool in that turn. Search right away when the product type is clear.

            Reply in 2-4 short sentences of plain text. The cards already show price, image and rating.
            """;

    private final OpenAiClient openAi;
    private final ShoppingTools tools;
    private final ProductService productService;
    private final ObjectMapper mapper;
    private final ChatProperties props;
    private final ChatSessionRepository sessions;

    public ChatResponse chat(String sessionId, String userMessage) {
        String id = sessionId == null || sessionId.isBlank() ? UUID.randomUUID().toString() : sessionId;
        ChatSession session = sessions.findById(id).orElseGet(() -> new ChatSession(id));
        List<JsonNode> messages = session.getMessages();
        int turnStart = messages.size();
        messages.add(message("user", userMessage));

        // If this throws (e.g. OpenAI is down), nothing is saved, so the failed turn leaves no trace.
        String reply = runAgent(session);

        // Keep only the shopper's message and the final reply. Tool calls and search results are the
        // bulk of the tokens; the system prompt restates what's still relevant (filters, shown products).
        messages.subList(turnStart, messages.size()).clear();
        messages.add(message("user", userMessage));
        messages.add(message("assistant", reply));
        // Keep the last N exchanges (2 messages each). The UI keeps the full chat; this only limits what OpenAI gets.
        while (messages.size() > 2 * props.getMaxHistoryTurns()) {
            messages.subList(0, 2).clear();
        }

        sessions.save(session);
        log.info("[{}] shown={} reply={}", id, session.getShownIds(), reply);
        return new ChatResponse(id, reply, productService.findByIds(session.getShownIds()), session.getActiveFilters(),
                session.getRecommendedId());
    }

    public void reset(String sessionId) {
        sessions.deleteById(sessionId);
    }

    private String runAgent(ChatSession session) {
        List<JsonNode> messages = session.getMessages();
        for (int step = 0; step < props.getMaxSteps(); step++) {
            List<JsonNode> request = new ArrayList<>();
            request.add(message("system", systemPrompt(session)));
            request.addAll(messages);

            JsonNode reply = openAi.chat(request, tools.definitions());
            JsonNode toolCalls = reply.path("tool_calls");
            ObjectNode assistant = message("assistant", reply.path("content").isTextual() ? reply.path("content").asText() : null);
            if (toolCalls.isArray() && !toolCalls.isEmpty()) {
                assistant.set("tool_calls", toolCalls);
            }
            messages.add(assistant);

            if (!toolCalls.isArray() || toolCalls.isEmpty()) {
                String text = reply.path("content").asText("");
                return text.isBlank() ? GAVE_UP : removeRepeatedLines(text);
            }
            for (JsonNode call : toolCalls) {
                String result = tools.execute(call.path("function").path("name").asText(), arguments(call), session);
                log.info("[{}] {} {} -> {}", session.getId(), call.path("function").path("name").asText(),
                        call.path("function").path("arguments").asText(), abbreviate(result));
                messages.add(message("tool", result).put("tool_call_id", call.path("id").asText()));
            }
        }
        return GAVE_UP;
    }

    /** Rules plus the current state, so follow-ups work without re-sending old search results. */
    private String systemPrompt(ChatSession session) {
        StringBuilder prompt = new StringBuilder(SYSTEM_PROMPT)
                .append("\nFilters of the last search: ").append(session.getActiveFilters())
                .append("\nProducts currently shown to the shopper, in order:");
        List<Product> shown = productService.findByIds(session.getShownIds());
        if (shown.isEmpty()) {
            prompt.append(" none");
        }
        for (int i = 0; i < shown.size(); i++) {
            Product p = shown.get(i);
            prompt.append("\n").append(i + 1).append(". id=").append(p.getId()).append(" | ").append(p.getTitle())
                    .append(" | brand: ").append(p.getBrand()).append(" | price: ").append(p.getPrice())
                    .append(" | listed colour: ").append(p.getColor()).append(" | listed size: ").append(p.getSize())
                    .append(" | material: ").append(p.getMaterial())
                    .append(" | features: ").append(String.join("; ", p.getFeatures().stream().limit(3).toList()));
        }
        return prompt.toString();
    }

    /**
     * Drops lines that exactly repeat an earlier line. With reasoning off, the model sometimes writes its
     * reply twice (e.g. a clarifying question followed by the same question), and prompting doesn't stop it.
     */
    static String removeRepeatedLines(String text) {
        Set<String> seen = new HashSet<>();
        return text.lines()
                .filter(line -> line.isBlank() || seen.add(line.strip()))
                .collect(Collectors.joining("\n"))
                .strip();
    }

    private static String abbreviate(String s) {
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }

    /** OpenAI sends tool arguments as a JSON string. */
    private JsonNode arguments(JsonNode call) {
        try {
            return mapper.readTree(call.path("function").path("arguments").asText("{}"));
        } catch (JsonProcessingException e) {
            return mapper.createObjectNode();
        }
    }

    private ObjectNode message(String role, String content) {
        return mapper.createObjectNode().put("role", role).put("content", content);
    }

    /** Visible for tests. */
    List<JsonNode> historyOf(String sessionId) {
        return sessions.findById(sessionId).map(s -> List.copyOf(s.getMessages())).orElse(List.of());
    }
}
