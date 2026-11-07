package com.shoppingassistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shoppingassistant.dto.ChatResponse;
import com.shoppingassistant.exception.LlmException;
import com.shoppingassistant.llm.OpenAiClient;
import com.shoppingassistant.model.Product;

/**
 * Real app wiring and fixture catalog; only the OpenAI client is scripted with Mockito.
 * The test profile sets chat.max-steps=4 and chat.max-history-turns=3.
 */
@SpringBootTest
@ActiveProfiles("test")
class ChatServiceTest {
    static final int MAX_STEPS = 4;

    @MockitoBean
    OpenAiClient openAi;
    @Autowired
    ChatService chat;

    final ObjectMapper mapper = new ObjectMapper();
    /** Copies of every request the agent sent to the model. */
    final List<List<JsonNode>> requests = new ArrayList<>();

    /** Scripts the model's replies, in order. */
    private void modelReplies(JsonNode... replies) {
        when(openAi.chat(any(), any())).thenAnswer(inv -> {
            requests.add(List.copyOf(inv.getArgument(0)));
            return replies[requests.size() - 1];
        });
    }

    private JsonNode toolCall(String name, String argumentsJson) {
        ObjectNode msg = mapper.createObjectNode().put("role", "assistant").putNull("content");
        msg.putArray("tool_calls").addObject().put("id", "call_" + name).put("type", "function")
                .putObject("function").put("name", name).put("arguments", argumentsJson);
        return msg;
    }

    private JsonNode text(String content) {
        return mapper.createObjectNode().put("role", "assistant").put("content", content);
    }

    private static List<String> ids(ChatResponse r) {
        return r.products().stream().map(Product::getId).toList();
    }

    @Test
    void searchesThenShowsChosenProductsFromTheDatabase() {
        modelReplies(
                toolCall("search_products", "{\"query\":\"running shoes\",\"max_price\":120}"),
                toolCall("show_products", "{\"ids\":[\"RS3\",\"RS1\"]}"),
                text("Two good options under $120."));

        ChatResponse r = chat.chat(null, "running shoes under $120");

        assertThat(r.message()).isEqualTo("Two good options under $120.");
        assertThat(ids(r)).containsExactly("RS3", "RS1");
        assertThat(r.products().get(0).getPrice()).isEqualTo(59.5); // price from the DB, not the model
        assertThat(r.activeFilters()).containsEntry("query", "running shoes").containsEntry("max_price", 120.0);
        assertThat(r.sessionId()).isNotBlank();
    }

    @Test
    void passesTheModelsTopPickThrough() {
        modelReplies(
                toolCall("search_products", "{\"query\":\"running shoes\"}"),
                toolCall("show_products", "{\"ids\":[\"RS3\",\"RS1\"],\"recommended\":\"RS3\"}"),
                text("RS3 is the best value."));

        ChatResponse r = chat.chat("s9", "running shoes");

        assertThat(r.recommendedId()).isEqualTo("RS3");
        assertThat(ids(r)).containsExactly("RS3", "RS1"); // all cards still sent; the UI decides what to show
    }

    @Test
    void ignoresATopPickThatIsNotAmongTheShownProducts() {
        modelReplies(
                toolCall("search_products", "{\"query\":\"running shoes\"}"),
                toolCall("show_products", "{\"ids\":[\"RS1\"],\"recommended\":\"MADE-UP\"}"),
                text("Here you go."));

        assertThat(chat.chat("s10", "running shoes").recommendedId()).isNull();
    }

    @Test
    void noTopPickWhenOnlyOneProductIsShown() {
        modelReplies(
                toolCall("search_products", "{\"query\":\"running shoes\"}"),
                toolCall("show_products", "{\"ids\":[\"RS1\"],\"recommended\":\"RS1\"}"),
                text("Here's one."));

        assertThat(chat.chat("s12", "running shoes").recommendedId()).isNull();
    }

    @Test
    void refusesToShowIdsTheModelWasNeverGiven() {
        modelReplies(
                toolCall("search_products", "{\"query\":\"running shoes\"}"),
                toolCall("show_products", "{\"ids\":[\"RS1\",\"MADE-UP\"]}"),
                text("Here you go."));

        ChatResponse r = chat.chat("s1", "running shoes");

        assertThat(ids(r)).containsExactly("RS1");
        JsonNode toolResult = requests.get(2).get(requests.get(2).size() - 1);
        assertThat(toolResult.path("content").asText()).contains("MADE-UP").contains("not shown");
    }

    @Test
    void showsTopSearchResultsIfTheModelForgetsShowProducts() {
        modelReplies(toolCall("search_products", "{\"query\":\"shorts\"}"), text("Some shorts."));

        assertThat(ids(chat.chat("s2", "shorts"))).containsExactlyInAnyOrder("SH1", "SH2", "SH3");
    }

    @Test
    void followUpSeesPreviousFiltersAndKeepsHistory() {
        modelReplies(
                toolCall("search_products", "{\"query\":\"running shoes\"}"),
                text("Here are running shoes."),
                toolCall("search_products", "{\"query\":\"running shoes\",\"color\":\"blue\"}"),
                text("Blue ones."));

        chat.chat("s3", "running shoes");
        ChatResponse r = chat.chat("s3", "only blue ones");

        List<JsonNode> followUp = requests.get(2);
        String systemPrompt = followUp.get(0).path("content").asText();
        assertThat(systemPrompt).contains("query=running shoes")
                .contains("id=RS1 | Men's Road Running Shoes").contains("price: 89.99"); // shown products restated
        // Earlier turn is sent as plain text only: no old tool calls or search results.
        assertThat(followUp).extracting(m -> m.path("role").asText()).containsExactly("system", "user", "assistant", "user");
        assertThat(followUp).extracting(m -> m.path("content").asText())
                .contains("running shoes", "Here are running shoes.", "only blue ones");
        assertThat(ids(r)).containsExactlyInAnyOrder("RS2", "RS3");
        assertThat(r.activeFilters()).containsEntry("color", "blue");
    }

    @Test
    void historyKeepsOnlyTextOfTheLastFewExchanges() {
        modelReplies(
                toolCall("search_products", "{\"query\":\"shorts\"}"), text("Reply 1"),
                text("Reply 2"), text("Reply 3"), text("Reply 4"));

        for (int i = 1; i <= 4; i++) {
            chat.chat("s8", "Message " + i);
        }

        // max-history-turns = 3, so message 1 is dropped; tool messages never stay in history.
        assertThat(chat.historyOf("s8")).extracting(m -> m.path("content").asText())
                .containsExactly("Message 2", "Reply 2", "Message 3", "Reply 3", "Message 4", "Reply 4");
    }

    @Test
    void dropsARepeatedLineFromTheModelsReply() {
        modelReplies(text("Would they prefer a backpack, boots, or a jacket?\nWould they prefer a backpack, boots, or a jacket?"));

        assertThat(chat.chat("s11", "a gift for a hiker").message()).isEqualTo("Would they prefer a backpack, boots, or a jacket?");
    }

    @Test
    void keepsDifferentLinesAndParagraphs() {
        assertThat(ChatService.removeRepeatedLines("Two options.\n\nThe first is cheaper.")).isEqualTo("Two options.\n\nThe first is cheaper.");
    }

    @Test
    void clarifyingQuestionKeepsCurrentProducts() {
        modelReplies(
                toolCall("search_products", "{\"query\":\"shorts\"}"),
                text("Some shorts."),
                text("Which size do you need?"));

        chat.chat("s4", "shorts");
        ChatResponse r = chat.chat("s4", "do these come in other sizes?");

        assertThat(r.message()).isEqualTo("Which size do you need?");
        assertThat(r.products()).hasSize(3);
    }

    @Test
    void failedLlmCallLeavesNoHalfFinishedTurnInHistory() {
        when(openAi.chat(any(), any())).thenThrow(new LlmException("down"));

        assertThatThrownBy(() -> chat.chat("s5", "hello")).isInstanceOf(LlmException.class);
        assertThat(chat.historyOf("s5")).isEmpty();
    }

    @Test
    void givesUpPolitelyAfterTooManyToolSteps() {
        JsonNode[] loop = new JsonNode[MAX_STEPS];
        java.util.Arrays.fill(loop, toolCall("search_products", "{\"query\":\"shoes\"}"));
        modelReplies(loop);

        assertThat(chat.chat("s6", "shoes").message()).isEqualTo(ChatService.GAVE_UP);
    }

    @Test
    void resetForgetsTheConversation() {
        modelReplies(text("Hi! What are you shopping for?"));
        chat.chat("s7", "hi");

        chat.reset("s7");

        assertThat(chat.historyOf("s7")).isEmpty();
    }
}
