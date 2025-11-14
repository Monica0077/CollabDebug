package com.collabdebug.collabdebug_backend.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Listens for session end events from Redis and broadcasts them to WebSocket clients.
 */
@Component
public class SessionEndListener implements MessageListener {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // 1. Deserialize the message body from JSON bytes
            String jsonPayload = new String(message.getBody());
            @SuppressWarnings("unchecked")
            Map<String, Object> endData = objectMapper.readValue(jsonPayload, Map.class);

            if (endData == null) {
                System.err.println("[SessionEndListener] ❌ Deserialized end data is NULL!");
                return;
            }

            // 2. Extract Session ID from the channel name
            // Channel name format: 'session-end:6a1f463f-ef51-416c-944f-f80000000000'
            String channel = new String(message.getChannel());
            String sessionId = channel.substring(channel.lastIndexOf(':') + 1);

            // 3. Broadcast to the STOMP topic
            String destination = "/topic/session/" + sessionId + "/end";

            messagingTemplate.convertAndSend(destination, endData);

            System.out.println("[SessionEndListener] ✅ Relayed session end event to WebSocket clients on topic: " + destination);

        } catch (Exception e) {
            System.err.println("[SessionEndListener] ❌ Error processing Redis session end message: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
