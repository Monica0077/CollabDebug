package com.collabdebug.collabdebug_backend.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Listens for presence updates from Redis and broadcasts them to WebSocket clients.
 * Handles user join/leave events in real-time.
 */
@Component
public class PresenceListener implements MessageListener {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // 0. Log that we received a message (verify listener is active)
            String channel = new String(message.getChannel());
            System.out.println("\n🟢 [PresenceListener] ✅ MESSAGE RECEIVED on channel: " + channel);

            // 1. Deserialize the message body as plain JSON (no type info needed)
            String jsonPayload = new String(message.getBody());
            @SuppressWarnings("unchecked")
            Map<String, Object> presenceData = objectMapper.readValue(jsonPayload, Map.class);

            if (presenceData == null) {
                System.err.println("[PresenceListener] ❌ Deserialized presence data is NULL!");
                return;
            }

            System.out.println("[PresenceListener] ✅ Deserialized presence data: " + presenceData);

            // 2. Extract Session ID from the channel name
            // Channel name format: 'session-presence:6a1f463f-ef51-416c-944f-f80000000000'
            String sessionId = channel.substring(channel.lastIndexOf(':') + 1);
            System.out.println("[PresenceListener] 📍 Session ID: " + sessionId);
            System.out.println("[PresenceListener] 👤 Event Type: " + presenceData.get("type"));
            System.out.println("[PresenceListener] 📝 User ID: " + presenceData.get("userId"));

            // 3. Broadcast to the STOMP topic so all WebSocket clients receive it instantly
            String destination = "/topic/session/" + sessionId + "/presence";

            // 🚨 CRITICAL STEP: Send the deserialized presence map to the STOMP topic
            System.out.println("[PresenceListener] 📤 Broadcasting to: " + destination);
            messagingTemplate.convertAndSend(destination, presenceData);

            System.out.println("[PresenceListener] ✅ SUCCESSFULLY relayed presence update to WebSocket clients");
            System.out.println("[PresenceListener] Complete data: " + presenceData);
            System.out.println("=".repeat(80) + "\n");

        } catch (Exception e) {
            System.err.println("[PresenceListener] ❌ ERROR processing Redis presence message: " + e.getMessage());
            System.err.println("[PresenceListener] Stack trace:");
            e.printStackTrace();
        }
    }
}
