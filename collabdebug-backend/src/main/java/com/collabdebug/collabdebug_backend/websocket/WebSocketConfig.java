package com.collabdebug.collabdebug_backend.websocket;

import com.collabdebug.collabdebug_backend.security.JwtHandshakeInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration; // <-- Needed import
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;         // <-- Needed import
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;   // <-- Needed import
import org.springframework.messaging.support.ChannelInterceptor;       // <-- Needed import
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal; // <-- Needed import

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Autowired
    private JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.setApplicationDestinationPrefixes("/app");
        config.enableSimpleBroker("/topic", "/queue");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/session")
                .addInterceptors(jwtHandshakeInterceptor) // This runs for HTTP Handshake
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    // 🚨 CRITICAL FIX: Propagate Principal from Session to STOMP Message
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

                // We now check for ALL commands that might require user context
                if (StompCommand.CONNECT.equals(accessor.getCommand()) ||
                        StompCommand.SEND.equals(accessor.getCommand()) ||
                        StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {

                    // 1. Get the 'principal' attribute set by JwtHandshakeInterceptor
                    Principal principal = (Principal) accessor.getSessionAttributes().get("principal");

                    if (principal != null) {
                        // 2. Set the Principal on the message header for ANY command type.
                        accessor.setUser(principal);
                        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                            System.out.println("STOMP CONNECT authenticated for user: " + principal.getName());
                        }
                    } else if (StompCommand.SEND.equals(accessor.getCommand())) {
                        // 3. Optional: Reject SEND if Principal is somehow missing (for debug/security)
                        System.err.println("STOMP SEND rejected: Principal not found in session attributes.");
                        // You can throw an exception here, but for now, just logging is fine.
                    }
                }
                return message;
            }
        });
    }
}