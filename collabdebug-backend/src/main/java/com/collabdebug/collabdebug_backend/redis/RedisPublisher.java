package com.collabdebug.collabdebug_backend.redis;

import com.collabdebug.collabdebug_backend.dto.ws.EditMessage;
import com.collabdebug.collabdebug_backend.dto.ws.ChatMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

@Component
public class RedisPublisher {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // Existing method
    public void publishEdit(EditMessage edit) {
        String channel = "session-updates:" + edit.sessionId;
        redisTemplate.convertAndSend(channel, edit);

        // persist master doc and version in Redis for durability if desired
        redisTemplate.opsForValue().set("session-doc:" + edit.sessionId, edit); // or store doc+version separately
    }

    // NEW: publish chat messages
    public void publishChat(ChatMessage chat) {
        String channel = "session-chat:" + chat.sessionId;
        redisTemplate.convertAndSend(channel, chat);

        // Optional: persist chat to Redis if needed for history
        redisTemplate.opsForList().rightPush("session-chat-history:" + chat.sessionId, chat);
    }
    // 🚨 NEW: Publish terminal output
    public void publishTerminalOutput(String sessionId, String output) {
        // Use a simple map/DTO for the terminal output payload if needed,
        // but a raw string often suffices.
        String channel = "session-terminal:" + sessionId;
        redisTemplate.convertAndSend(channel, output);
    }

    // 🚨 NEW: Publish presence updates
    public void publishPresence(String sessionId, Object payload) {
        String channel = "session-presence:" + sessionId;
        redisTemplate.convertAndSend(channel, payload);
    }
}
