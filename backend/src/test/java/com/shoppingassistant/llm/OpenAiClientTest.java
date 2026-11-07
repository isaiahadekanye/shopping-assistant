package com.shoppingassistant.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoppingassistant.config.OpenAiProperties;
import com.shoppingassistant.exception.LlmException;

class OpenAiClientTest {
    final ObjectMapper mapper = new ObjectMapper();

    OpenAiProperties props;

    private OpenAiClient client(String baseUrl, String model) {
        props = new OpenAiProperties();
        props.setBaseUrl(baseUrl);
        props.setApiKey("sk-test");
        props.setModel(model);
        props.setTimeoutSeconds(5);
        return new OpenAiClient(props, mapper);
    }

    @Test
    void postsConversationAndToolsAndReturnsAssistantMessage() throws Exception {
        try (StubServer server = new StubServer()) {
            server.reply(200, """
                    {"choices":[{"message":{"role":"assistant","content":null,
                      "tool_calls":[{"id":"c1","type":"function","function":{"name":"search_products","arguments":"{}"}}]}}]}""");

            JsonNode reply = client(server.url() + "/v1", "gpt-test")
                    .chat(List.of(mapper.readTree("{\"role\":\"user\",\"content\":\"hi\"}")), mapper.readTree("[]"));

            assertThat(reply.path("tool_calls").path(0).path("function").path("name").asText()).isEqualTo("search_products");
            StubServer.Recorded request = server.requests.get(0);
            assertThat(request.path()).isEqualTo("/v1/chat/completions");
            assertThat(request.headers().get("Authorization")).containsExactly("Bearer sk-test");
            JsonNode body = mapper.readTree(request.body());
            assertThat(body.path("model").asText()).isEqualTo("gpt-test");
            assertThat(body.path("messages").path(0).path("content").asText()).isEqualTo("hi");
            assertThat(body.path("tools").isArray()).isTrue();
        }
    }

    @Test
    void sendsReasoningEffortOnlyWhenSet() throws Exception {
        String reply = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"hi\"}}]}";
        try (StubServer server = new StubServer()) {
            server.reply(200, reply).reply(200, reply);
            OpenAiClient client = client(server.url(), "gpt-test");

            client.chat(List.of(), mapper.readTree("[]"));
            props.setReasoningEffort("none");
            client.chat(List.of(), mapper.readTree("[]"));

            assertThat(mapper.readTree(server.requests.get(0).body()).has("reasoning_effort")).isFalse();
            assertThat(mapper.readTree(server.requests.get(1).body()).path("reasoning_effort").asText()).isEqualTo("none");
        }
    }

    @Test
    void httpErrorsBecomeLlmException() throws Exception {
        try (StubServer server = new StubServer()) {
            server.reply(401, "{\"error\":{\"message\":\"bad key\"}}");

            assertThatThrownBy(() -> client(server.url(), "gpt-test").chat(List.of(), mapper.readTree("[]")))
                    .isInstanceOf(LlmException.class).hasMessageContaining("401").hasMessageContaining("bad key");
        }
    }

    @Test
    void missingModelExplainsHowToConfigure() {
        assertThatThrownBy(() -> client("http://unused", "").chat(List.of(), mapper.createArrayNode()))
                .isInstanceOf(LlmException.class).hasMessageContaining("not configured");
    }
}
