package com.collabdebug.collabdebug_backend.redis;

import com.collabdebug.collabdebug_backend.dto.ws.EditMessage;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

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
        try {
            // messageBody is the JSON string (with the ignored @class field)
            EditMessage edit = objectMapper.readValue(messageBody, EditMessage.class);

            // forward to local websocket clients subscribed to this session
            template.convertAndSend("/topic/session/" + edit.sessionId + "/edits", edit);
        } catch (Exception e) {
            System.err.println("Error deserializing message from Redis: " + messageBody);
            e.printStackTrace();
        }
    }
}