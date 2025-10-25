package com.collabdebug.collabdebug_backend.config;

import com.collabdebug.collabdebug_backend.redis.RedisMessageSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        // Assuming Redis runs on localhost:6379
        return new LettuceConnectionFactory("localhost", 6379);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory cf) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(cf);
        template.setKeySerializer(new StringRedisSerializer());

        // Using GenericJackson2JsonRedisSerializer for object serialization
        // This is what adds the @class field.
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisMessageListenerContainer redisContainer(RedisConnectionFactory cf,
                                                        MessageListenerAdapter listenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(cf);
        // Topic pattern matching the key used by RedisPublisher
        container.addMessageListener(listenerAdapter, new PatternTopic("session-updates:*"));
        return container;
    }

    @Bean
    public MessageListenerAdapter listenerAdapter(RedisMessageSubscriber subscriber) {
        // Ensures the raw message body (as String) is passed to onMessage(String)
        MessageListenerAdapter adapter = new MessageListenerAdapter(subscriber, "onMessage");
        // IMPORTANT: Ensure the serializer for the message body is StringRedisSerializer
        // so the subscriber receives a simple JSON string.
        adapter.setSerializer(new StringRedisSerializer());
        return adapter;
    }
}