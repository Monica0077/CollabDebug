// File: com.collabdebug.collabdebug_backend.redis.TerminalOutputListener.java

package com.collabdebug.collabdebug_backend.redis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class TerminalOutputListener implements MessageListener {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // 1. Deserialize the message body (which is the output string)
            // Assumes your RedisTemplate is configured with String/JSON serializers
            String output = (String) redisTemplate.getValueSerializer().deserialize(message.getBody());

            // 2. Extract Session ID from the channel name
            // Channel name format: 'session-terminal:6a1f463f-ef51-416c-944f-f80000000000'
            String channel = new String(message.getChannel());
            String sessionId = channel.substring(channel.lastIndexOf(':') + 1);

            // 3. Broadcast to the STOMP topic
            String destination = "/topic/session/" + sessionId + "/terminal";

            // 🚨 CRITICAL STEP: Send the raw output string to the STOMP topic
            messagingTemplate.convertAndSend(destination, output);

            System.out.println("Terminal output relayed to WebSocket clients on topic: " + destination);

        } catch (Exception e) {
            System.err.println("Error processing Redis terminal message: " + e.getMessage());
            e.printStackTrace();
        }
    }
}