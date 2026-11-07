package com.shoppingassistant.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shoppingassistant.model.ChatSession;

public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {
}
