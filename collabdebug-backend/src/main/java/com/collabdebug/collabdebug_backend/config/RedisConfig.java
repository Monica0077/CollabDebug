package com.collabdebug.collabdebug_backend.config;

import com.collabdebug.collabdebug_backend.redis.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 🚨 CRITICAL FIX: Redis configuration now registers ALL message listeners
 * for complete real-time support:
 * 
 * 1. TerminalOutputListener - terminal execution output
 * 2. ChatMessageListener - chat messages
 * 3. EditMessageListener - collaborative code edits
 * 4. PresenceListener - participant join/leave events (THIS WAS MISSING!)
 * 5. SessionMetaListener - session metadata changes (language, etc.)
 * 6. SessionEndListener - session end notifications
 * 
 * This ensures all changes are instantly propagated via WebSocket to connected clients.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory("localhost", 6379);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory cf) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(cf);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 🚨 CRITICAL: Register ALL Redis message listeners for complete real-time coverage
     */
    @Bean
    public RedisMessageListenerContainer container(
            RedisConnectionFactory connectionFactory,
            TerminalOutputListener terminalOutputListener,
            ChatMessageListener chatMessageListener,
            EditMessageListener editMessageListener,
            PresenceListener presenceListener,
            SessionMetaListener sessionMetaListener,
            SessionEndListener sessionEndListener) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // Register terminal output listener
        container.addMessageListener(terminalOutputListener,
                new PatternTopic("session-terminal:*"));

        // Register chat message listener
        container.addMessageListener(chatMessageListener,
                new PatternTopic("session-chat:*"));

        // Register edit message listener (for collaborative editing)
        container.addMessageListener(editMessageListener,
                new PatternTopic("session-updates:*"));

        // 🚨 CRITICAL FIX: Register presence listener for participant join/leave events
        // This was the MISSING piece causing instant updates to not work!
        container.addMessageListener(presenceListener,
                new PatternTopic("session-presence:*"));

        // Register session metadata listener (language changes, etc.)
        container.addMessageListener(sessionMetaListener,
                new PatternTopic("session-meta:*"));

        // Register session end listener
        container.addMessageListener(sessionEndListener,
                new PatternTopic("session-end:*"));

        System.out.println("[RedisConfig] ✅ All Redis message listeners registered successfully!");
        System.out.println("[RedisConfig] Listening to channels:");
        System.out.println("  - session-terminal:*");
        System.out.println("  - session-chat:*");
        System.out.println("  - session-updates:*");
        System.out.println("  - session-presence:* (NOW ACTIVE - FIX!)");
        System.out.println("  - session-meta:*");
        System.out.println("  - session-end:*");

        return container;
    }
}