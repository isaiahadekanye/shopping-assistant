package com.shoppingassistant.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shoppingassistant.config.OpenAiProperties;
import com.shoppingassistant.exception.LlmException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OpenAiClient {
    private final OpenAiProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newHttpClient();

    /**
     * Sends the conversation plus tool definitions and returns the assistant message,
     * which has "content" and "tool_calls".
     */
    public JsonNode chat(List<JsonNode> messages, JsonNode tools) {
        if (props.getModel() == null || props.getModel().isBlank()) {
            throw new LlmException("OpenAI is not configured.");
        }
        ObjectNode body = mapper.createObjectNode().put("model", props.getModel());
        body.putArray("messages").addAll(messages);
        body.set("tools", tools);
        if (props.getReasoningEffort() != null && !props.getReasoningEffort().isBlank()) {
            body.put("reasoning_effort", props.getReasoningEffort());
        }

        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(props.getBaseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (props.getApiKey() != null && !props.getApiKey().isBlank()) {
            request.header("Authorization", "Bearer " + props.getApiKey());
        }

        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new LlmException("OpenAI returned HTTP " + response.statusCode() + ": " + abbreviate(response.body()));
            }
            return mapper.readTree(response.body()).path("choices").path(0).path("message");
        } catch (IOException e) {
            throw new LlmException("Could not reach OpenAI: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Interrupted while calling OpenAI", e);
        }
    }

    private static String abbreviate(String s) {
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
