// File: com.collabdebug.collabdebug_backend.redis.ChatMessageListener.java 
// (or integrate this logic into a single MessageListener class)

package com.collabdebug.collabdebug_backend.redis;

import com.collabdebug.collabdebug_backend.dto.ws.ChatMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class ChatMessageListener implements MessageListener {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // 1. Deserialize the message body into the ChatMessage DTO
            ChatMessage chatMessage = (ChatMessage) redisTemplate.getValueSerializer()
                    .deserialize(message.getBody());

            if (chatMessage == null) return;

            String sessionId = chatMessage.getSessionId();

            // 2. Broadcast to the STOMP topic
            String destination = "/topic/session/" + sessionId + "/chat";

            // 🚨 CRITICAL STEP: Send the deserialized ChatMessage object
            messagingTemplate.convertAndSend(destination, chatMessage);

            System.out.println("Chat message relayed to WebSocket clients on topic: " + destination);

        } catch (Exception e) {
            System.err.println("Error processing Redis chat message: " + e.getMessage());
        }
    }
}