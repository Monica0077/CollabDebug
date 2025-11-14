package com.collabdebug.collabdebug_backend.websocket;

import com.collabdebug.collabdebug_backend.service.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.security.Principal;

/**
 * Listens for STOMP UNSUBSCRIBE events to handle when users manually leave topics.
 * This is separate from full disconnects - handles cases where a user unsubscribes from presence topic.
 */
@Component
public class WebSocketUnsubscribeListener {

    @Autowired
    private SessionService sessionService;

    @EventListener
    public void handleSessionUnsubscribe(SessionUnsubscribeEvent event) {
        try {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
            String dest = accessor.getDestination();
            Principal principal = (Principal) accessor.getSessionAttributes().get("principal");
            String connectionId = accessor.getSessionId();

            System.out.println("\n🟡 [WebSocketUnsubscribeListener] UNSUBSCRIBE EVENT RECEIVED");
            System.out.println("[WebSocketUnsubscribeListener] 📍 Destination: " + dest);
            System.out.println("[WebSocketUnsubscribeListener] 👤 Principal: " + (principal != null ? principal.getName() : "NULL"));
            System.out.println("[WebSocketUnsubscribeListener] 🔗 Connection ID: " + connectionId);

            // Only handle presence topic unsubscribes (when explicitly leaving)
            if (dest != null && dest.contains("/presence")) {
                System.out.println("[WebSocketUnsubscribeListener] ✅ Presence topic unsubscribe detected");
                
                if (dest.startsWith("/topic/session/")) {
                    String[] parts = dest.split("/");
                    if (parts.length > 3) {
                        String sessionId = parts[3];
                        System.out.println("[WebSocketUnsubscribeListener] 📍 Session ID: " + sessionId);
                        
                        if (principal != null) {
                            System.out.println("[WebSocketUnsubscribeListener] 📤 Publishing leave event to Redis");
                            sessionService.publishUserLeft(sessionId, principal.getName());
                            System.out.println("[WebSocketUnsubscribeListener] ✅ Leave event published");
                        } else {
                            System.err.println("[WebSocketUnsubscribeListener] ❌ Principal is NULL - cannot publish leave event!");
                        }
                    }
                }
            } else {
                System.out.println("[WebSocketUnsubscribeListener] ℹ️ Not a presence topic, skipping: " + dest);
            }
            System.out.println("=".repeat(80) + "\n");

        } catch (Exception e) {
            System.err.println("[WebSocketUnsubscribeListener] ❌ Error handling unsubscribe event: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
