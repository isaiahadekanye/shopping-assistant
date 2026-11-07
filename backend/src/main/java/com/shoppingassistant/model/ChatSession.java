package com.shoppingassistant.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "chat_sessions")
@Getter
@Setter
@NoArgsConstructor
public class ChatSession {
    @Id
    private String id;

    /** Rejects a save if another request updated this session in the meantime. */
    @Version
    private Long version;

    /**
     * Conversation sent to OpenAI. Between turns it holds only shopper messages and final replies;
     * tool calls and results exist only while a turn runs.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<JsonNode> messages = new ArrayList<>();

    /** Ids returned by searches in this session; the model may only display these. */
    @JdbcTypeCode(SqlTypes.JSON)
    private Set<String> seenIds = new HashSet<>();

    /** Products on screen: the top search results, or the ones the model picked with show_products. */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> shownIds = new ArrayList<>();

    /** The model's top pick among the shown products, if it named one. */
    private String recommendedId;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> activeFilters = new LinkedHashMap<>();

    public ChatSession(String id) {
        this.id = id;
    }
}
