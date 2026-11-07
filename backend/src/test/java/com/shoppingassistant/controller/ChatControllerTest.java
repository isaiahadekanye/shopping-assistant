package com.shoppingassistant.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.shoppingassistant.dto.ChatResponse;
import com.shoppingassistant.exception.LlmException;
import com.shoppingassistant.model.Product;
import com.shoppingassistant.service.ChatService;

@WebMvcTest(ChatController.class)
class ChatControllerTest {
    @Autowired
    MockMvc mvc;
    @MockitoBean
    ChatService chatService;

    @Test
    void returnsReplyProductsAndFilters() throws Exception {
        Product card = Product.builder().id("RS1").title("Road Runner").price(89.99).searchText(" road runner").build();
        when(chatService.chat(null, "running shoes")).thenReturn(
                new ChatResponse("s1", "Here you go.", List.of(card), Map.of("query", "running shoes"), "RS1"));

        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"  running shoes \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("s1"))
                .andExpect(jsonPath("$.products[0].price").value(89.99))
                .andExpect(jsonPath("$.products[0].searchText").doesNotExist())
                .andExpect(jsonPath("$.activeFilters.query").value("running shoes"))
                .andExpect(jsonPath("$.recommendedId").value("RS1"));
    }

    @Test
    void blankMessageIs400() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void llmFailureIs502WithMessage() throws Exception {
        when(chatService.chat(any(), any())).thenThrow(new LlmException("OpenAI is not configured."));

        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("OpenAI is not configured."));
    }

    @Test
    void deleteResetsSession() throws Exception {
        mvc.perform(delete("/api/chat/s1")).andExpect(status().isNoContent());
        verify(chatService).reset("s1");
    }
}
