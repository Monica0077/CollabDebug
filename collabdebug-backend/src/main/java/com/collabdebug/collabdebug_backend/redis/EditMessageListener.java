package com.collabdebug.collabdebug_backend.redis;

import com.collabdebug.collabdebug_backend.dto.ws.EditMessage;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Listens for edit messages from Redis and broadcasts them to WebSocket clients.
 * Ensures all instances in a distributed deployment receive edit updates.
 */
@Component
public class EditMessageListener implements MessageListener {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final ObjectMapper objectMapper;

    public EditMessageListener() {
        this.objectMapper = new ObjectMapper();
        // Ignore unknown properties from JSON serialization
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // 1. Deserialize the message body from JSON bytes
            String messageBody = new String(message.getBody());
            EditMessage editMessage = objectMapper.readValue(messageBody, EditMessage.class);

            if (editMessage == null) {
                System.err.println("[EditMessageListener] ❌ Deserialized edit message is NULL!");
                return;
            }

            // 2. Extract Session ID from the message itself
            String sessionId = editMessage.sessionId;

            // 3. Broadcast to the STOMP topic so all WebSocket clients receive it instantly
            String destination = "/topic/session/" + sessionId + "/edits";

            // 🚨 CRITICAL STEP: Send the deserialized EditMessage to the STOMP topic
            messagingTemplate.convertAndSend(destination, editMessage);

            System.out.println("[EditMessageListener] ✅ Relayed edit message to WebSocket clients on topic: " + destination);
            System.out.println("[EditMessageListener] 📝 Edit from user: " + editMessage.userId);

        } catch (Exception e) {
            System.err.println("[EditMessageListener] ❌ Error processing Redis edit message: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
