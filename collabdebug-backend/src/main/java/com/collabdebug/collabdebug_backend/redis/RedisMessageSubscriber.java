package com.collabdebug.collabdebug_backend.redis;

import com.collabdebug.collabdebug_backend.dto.ws.ChatMessage;
import com.collabdebug.collabdebug_backend.dto.ws.EditMessage;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class RedisMessageSubscriber {

    @Autowired
    private SimpMessagingTemplate template;

    private final ObjectMapper objectMapper;

    // Constructor to configure the ObjectMapper
    public RedisMessageSubscriber() {
        this.objectMapper = new ObjectMapper();
        // 🚨 CRITICAL FIX: Ignore the "@class" field added by Redis serializer
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    // Must be public and take (String, String) if MessageListenerAdapter is configured
    // to use StringRedisSerializer for the body.
    public void onMessage(String messageBody, String channel) {
        String sessionId = channel.substring(channel.lastIndexOf(":") + 1);
        try {
            if (channel.startsWith("session-updates:")) {
                // 1. Edits
                EditMessage edit = objectMapper.readValue(messageBody, EditMessage.class);
                template.convertAndSend("/topic/session/" + sessionId + "/edits", edit);

            } else if (channel.startsWith("session-chat:")) {
                // 2. Chat Messages
                ChatMessage chat = objectMapper.readValue(messageBody, ChatMessage.class);
                template.convertAndSend("/topic/session/" + sessionId + "/chat", chat);

            } else if (channel.startsWith("session-terminal:")) {
                // 3. Terminal Output (assuming it's a raw String payload)
                // Use Object.class because the publisher sends the raw string, which Jackson wraps/treats as object.
                String output = objectMapper.readValue(messageBody, String.class);
                template.convertAndSend("/topic/session/" + sessionId + "/terminal", output);

            } else if (channel.startsWith("session-presence:")) {
                // 4. Presence Updates (for participant list fix)
                // Assuming it's a Map<String, String> like {"type": "joined", "userId": "user"}
                Map<String, Object> presence = objectMapper.readValue(messageBody, Map.class);
                template.convertAndSend("/topic/session/" + sessionId + "/presence", presence);

            } else {
                System.err.println("WARN: Received message on unknown Redis channel: " + channel);
            }

        } catch (Exception e) {
            System.err.printf("Error deserializing message for channel %s: %s%n", channel, messageBody);
            e.printStackTrace();
        }
    }
}