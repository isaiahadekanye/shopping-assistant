package com.shoppingassistant.llm;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoppingassistant.config.OpenAiProperties;
import com.shoppingassistant.exception.LlmException;

class OpenAiClientTest {

    @Test
    void missingModelExplainsHowToConfigure() {
        ObjectMapper mapper = new ObjectMapper();
        OpenAiClient client = new OpenAiClient(new OpenAiProperties(), mapper);

        assertThatThrownBy(() -> client.chat(List.of(), mapper.createArrayNode()))
                .isInstanceOf(LlmException.class).hasMessageContaining("not configured");
    }
}
