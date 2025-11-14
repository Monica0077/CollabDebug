package com.collabdebug.collabdebug_backend.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Listens for session metadata updates (language changes, etc.) from Redis
 * and broadcasts them to WebSocket clients.
 */
@Component
public class SessionMetaListener implements MessageListener {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // 1. Deserialize the message body from JSON bytes
            String jsonPayload = new String(message.getBody());
            @SuppressWarnings("unchecked")
            Map<String, Object> metaData = objectMapper.readValue(jsonPayload, Map.class);

            if (metaData == null) {
                System.err.println("[SessionMetaListener] ❌ Deserialized metadata is NULL!");
                return;
            }

            // 2. Extract Session ID from the channel name
            // Channel name format: 'session-meta:6a1f463f-ef51-416c-944f-f80000000000'
            String channel = new String(message.getChannel());
            String sessionId = channel.substring(channel.lastIndexOf(':') + 1);

            // 3. Broadcast to the STOMP topic
            String destination = "/topic/session/" + sessionId + "/meta";

            messagingTemplate.convertAndSend(destination, metaData);

            System.out.println("[SessionMetaListener] ✅ Relayed session metadata to WebSocket clients on topic: " + destination);

        } catch (Exception e) {
            System.err.println("[SessionMetaListener] ❌ Error processing Redis session meta message: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
