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

    // 🚨 CRITICAL FIX: Use principal from HTTP handshake (JwtHandshakeInterceptor)
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                StompCommand command = accessor.getCommand();

                // 🔴 ON CONNECT: Extract principal from session attributes (set by JwtHandshakeInterceptor)
                if (StompCommand.CONNECT.equals(command)) {
                    // The principal was set by JwtHandshakeInterceptor in HTTP handshake attributes
                    Principal principal = (Principal) accessor.getSessionAttributes().get("principal");
                    
                    if (principal != null) {
                        // Store principal on the message so EventListener can access it
                        accessor.setUser(principal);
                        System.out.println("🟢 STOMP CONNECT authenticated for user: " + principal.getName());
                    } else {
                        // Debug: print all session attributes
                        System.err.println("❌ STOMP CONNECT: Principal not found in session attributes");
                        System.err.println("   Available session attributes: " + accessor.getSessionAttributes().keySet());
                    }
                }
                // 🟢 ON SUBSCRIBE/SEND: Principal should already be in session attributes from HTTP handshake
                else if (StompCommand.SUBSCRIBE.equals(command) || StompCommand.SEND.equals(command)) {
                    Principal principal = (Principal) accessor.getSessionAttributes().get("principal");
                    
                    if (principal != null) {
                        accessor.setUser(principal);
                        
                        if (StompCommand.SUBSCRIBE.equals(command)) {
                            String dest = accessor.getDestination();
                            System.out.println("🟢 STOMP SUBSCRIBE authenticated for user: " + principal.getName() + " to: " + dest);
                        }
                    } else {
                        String dest = accessor.getDestination() != null ? accessor.getDestination() : "unknown";
                        System.err.println("❌ STOMP " + command + ": No principal in session attributes for destination: " + dest);
                        System.err.println("   Available session attributes: " + accessor.getSessionAttributes().keySet());
                    }
                }
                
                return message;
            }
        });
    }
}