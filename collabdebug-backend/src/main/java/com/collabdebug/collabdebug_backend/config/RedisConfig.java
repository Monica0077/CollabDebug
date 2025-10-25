// FINAL FIXED RedisConfig.java

package com.collabdebug.collabdebug_backend.config;

import com.collabdebug.collabdebug_backend.redis.ChatMessageListener; // Make sure these are back
import com.collabdebug.collabdebug_backend.redis.TerminalOutputListener; // Make sure these are back
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
// NOTE: We remove the import for MessageListenerAdapter
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

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

    // 🚨 We only define ONE container bean, registering all specific listeners here.
    @Bean
    public RedisMessageListenerContainer container(
            RedisConnectionFactory connectionFactory,
            TerminalOutputListener terminalOutputListener,
            ChatMessageListener chatMessageListener) { // Inject your listeners

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // Register ALL topics with their dedicated listener components.
        container.addMessageListener(terminalOutputListener,
                new PatternTopic("session-terminal:*"));

        container.addMessageListener(chatMessageListener,
                new PatternTopic("session-chat:*"));

        // 🚨 IMPORTANT: You must also register listeners for edits and presence!
        // Assuming you have an EditMessageListener and PresenceListener, or use a single listener.
        // If not, you need to create them and inject them here.
        // For now, let's focus on the two that were duplicating.

        // If you were using the all-in-one RedisMessageSubscriber before, you must
        // re-add it here using MessageListenerAdapter if you want to keep it.
        // OR you create dedicated listeners for 'session-updates' and 'session-presence'.

        return container;
    }

    // 🚨 DELETE the listenerAdapter bean if you are using dedicated listeners,
    // OR if you use a single listener, you must use it here, but ONLY here.

    // Since the original was complex, let's assume you need one more combined listener
    // to handle the remaining topics ('session-updates' and 'session-presence').

    // If you need the complexity of MessageListenerAdapter, you must define it
    // and ONLY register its adapter here.

    // Assuming the original 'redisContainer' was handling all 4 topics,
    // and now you want two of them handled explicitly:

    // Final recommendation is to put all channels on the ONLY ONE RedisMessageListenerContainer
    // bean definition. Since you have dedicated classes for Chat/Terminal,
    // stick to that pattern for the other two channels as well to avoid the conflict.
}