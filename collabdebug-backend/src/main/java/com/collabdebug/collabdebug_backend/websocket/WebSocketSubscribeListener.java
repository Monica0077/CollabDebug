package com.collabdebug.collabdebug_backend.websocket;

import com.collabdebug.collabdebug_backend.service.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.security.Principal;

/**
 * Listens for STOMP subscribe events and registers presence via SessionService.
 * Kept separate from WebSocketConfig to avoid circular bean references.
 */
@Component
public class WebSocketSubscribeListener {

    @Autowired
    private SessionService sessionService;

    @EventListener
    public void handleSessionSubscribe(SessionSubscribeEvent event) {
        try {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
            String dest = accessor.getDestination();
            
            // 🚨 CRITICAL: Get principal from session attributes, NOT from accessor.getUser()
            // because EventListener receives the ORIGINAL message before preSend interceptor processes it
            Principal principal = (Principal) accessor.getSessionAttributes().get("principal");
            String connectionId = accessor.getSessionId();

            System.out.println("\n🟢 [WebSocketSubscribeListener] SUBSCRIBE EVENT RECEIVED");
            System.out.println("[WebSocketSubscribeListener] 📍 Destination: " + dest);
            System.out.println("[WebSocketSubscribeListener] 👤 Principal: " + (principal != null ? principal.getName() : "NULL"));
            System.out.println("[WebSocketSubscribeListener] 🔗 Connection ID: " + connectionId);

            if (dest != null && dest.startsWith("/topic/session/")) {
                System.out.println("[WebSocketSubscribeListener] ✅ Matches /topic/session/ pattern");
                String[] parts = dest.split("/");
                System.out.println("[WebSocketSubscribeListener] 📊 Path parts count: " + parts.length);
                
                if (parts.length > 3) {
                    String sessionId = parts[3];
                    System.out.println("[WebSocketSubscribeListener] 📍 Extracted Session ID: " + sessionId);
                    
                    if (principal != null) {
                        System.out.println("[WebSocketSubscribeListener] 📤 Calling sessionService.userJoined()");
                        sessionService.userJoined(sessionId, connectionId, principal.getName());
                        System.out.println("[WebSocketSubscribeListener] ✅ userJoined() completed");
                    } else {
                        System.err.println("[WebSocketSubscribeListener] ❌ Principal is NULL - cannot register presence!");
                        System.err.println("[WebSocketSubscribeListener] ❌ Available session attributes: " + accessor.getSessionAttributes().keySet());
                    }
                } else {
                    System.err.println("[WebSocketSubscribeListener] ❌ Path doesn't have enough parts: " + parts.length);
                }
            } else {
                System.out.println("[WebSocketSubscribeListener] ⏭️ Not a session topic, skipping: " + dest);
            }
            System.out.println("=".repeat(80) + "\n");

        } catch (Exception e) {
            System.err.println("[WebSocketSubscribeListener] ❌ Error handling subscribe event: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
