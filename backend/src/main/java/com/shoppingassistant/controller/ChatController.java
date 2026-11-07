package com.shoppingassistant.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shoppingassistant.dto.ChatRequest;
import com.shoppingassistant.dto.ChatResponse;
import com.shoppingassistant.service.ChatService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return chatService.chat(request.sessionId(), request.message().trim());
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> reset(@PathVariable String sessionId) {
        chatService.reset(sessionId);
        return ResponseEntity.noContent().build();
    }
}
