package com.collabdebug.collabdebug_backend.websocket;

import com.collabdebug.collabdebug_backend.service.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Listens for WebSocket disconnect events and informs the SessionService so
 * presence 'left' events can be published if needed.
 */
@Component
public class WebSocketEventListener implements ApplicationListener<SessionDisconnectEvent> {

    @Autowired
    private SessionService sessionService;

    @Override
    public void onApplicationEvent(SessionDisconnectEvent event) {
        try {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
            String connectionId = accessor.getSessionId();
            
            System.out.println("\n🔴 [WebSocketEventListener] DISCONNECT EVENT RECEIVED");
            System.out.println("[WebSocketEventListener] 🔗 Connection ID: " + connectionId);
            
            if (connectionId != null) {
                System.out.println("[WebSocketEventListener] 📤 Calling sessionService.connectionClosed()");
                sessionService.connectionClosed(connectionId);
                System.out.println("[WebSocketEventListener] ✅ connectionClosed() completed");
            } else {
                System.err.println("[WebSocketEventListener] ❌ Connection ID is NULL!");
            }
            System.out.println("=".repeat(80) + "\n");
            
        } catch (Exception e) {
            System.err.println("[WebSocketEventListener] ❌ Error handling websocket disconnect: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
