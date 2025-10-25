package com.collabdebug.collabdebug_backend.websocket;


import com.collabdebug.collabdebug_backend.security.JwtHandshakeInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Autowired
    private JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // We will use application destination for incoming client->server
        config.setApplicationDestinationPrefixes("/app");
        // Use simple broker for local dispatch; Redis will propagate cross-instance
        config.enableSimpleBroker("/topic", "/queue");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/session")
                .addInterceptors(jwtHandshakeInterceptor) // attach principal
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}

