package com.collabdebug.collabdebug_backend.controller;

import com.collabdebug.collabdebug_backend.dto.ws.ChatMessage;
import com.collabdebug.collabdebug_backend.dto.ws.EditMessage;
import com.collabdebug.collabdebug_backend.dto.ws.EditResponse;
import com.collabdebug.collabdebug_backend.redis.RedisPublisher;
import com.collabdebug.collabdebug_backend.service.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate; // 🚨 New Import
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class CollabController {
    @Autowired
    private SessionService sessionService;
    @Autowired
    private RedisPublisher redisPublisher;
    @Autowired
    private SimpMessagingTemplate messagingTemplate; // 🚨 Inject the template

    @MessageMapping("/session/{sessionId}/edit")
    public void receiveEdit(@DestinationVariable String sessionId,
                            @Payload EditMessage edit,
                            Principal principal) {

        // --- Temporary Security Bypass & Logging ---
        // 🚨 CRITICAL: The NullPointerException means principal is null.
        // DO NOT uncomment `edit.userId = principal.getName();` until security is fixed.
        // We rely on the client-sent userId (unsafe but works for testing).
        String userId = edit.userId;
        System.out.println("Received edit from client ID: " + userId + " for session: " + sessionId);

        // --- Core Collaboration Logic ---
        EditResponse res = sessionService.applyEdit(edit);

        if (res.applied) {
            // 1. Publish to Redis (for multi-instance scaling)
            redisPublisher.publishEdit(edit);

            // 2. 🚨 CRITICAL FIX: Send the message back to ALL subscribers on the topic
            // Note: The Redis message listener *should* be doing this if using a broker config.
            // If not, explicitly send to the destination topic.
            // The frontend is subscribed to: /topic/session/{sessionId}/edits
//            messagingTemplate.convertAndSend(
//                    "/topic/session/" + sessionId + "/edits",
//                    edit // Send the original edit message to the topic
//            );

        } else {
            // Send back current doc so client can resync (Optimization)
            sessionService.replyToUser(edit.userId, "/queue/edits", res);
        }
    }

    @MessageMapping("/session/{sessionId}/chat")
    public void receiveChat(@DestinationVariable String sessionId, @Payload ChatMessage chat, Principal principal){
        String userIdFromAuth = null;

        // 1. Try to get the user ID from the authenticated Principal
        if (principal != null) {
            userIdFromAuth = principal.getName();
            // Optional: Validate that the user in the principal matches the user in the payload
            if (!userIdFromAuth.equals(chat.getUserId())) {
                System.err.println("SECURITY WARNING: Auth principal (" + userIdFromAuth + ") does not match payload user (" + chat.getUserId() + ")");
                // For now, trust the authenticated user, but use payload's user for display if principal is null
            }
        }

        // 2. 🚨 FIX THE ANONYMOUS ID: If principal is null, rely on the client-provided userId
        if (principal == null) {
            System.err.println("WARNING: Principal is null. Relying on payload userId: " + chat.getUserId());
            // The client provides the correct ID, so we trust it here to fix the display issue.
        } else {
            // If authenticated, overwrite the payload's userId with the secure principal's name
            chat.setUserId(userIdFromAuth);
        }

        // store in DB if needed, then publish
        redisPublisher.publishChat(chat);

        // 3. 🚨 FIX DUPLICATION: Ensure you DO NOT send the message directly to the topic here.
        // The Redis Listener is responsible for picking it up and broadcasting it.
        // Keep this part commented out:
//        messagingTemplate.convertAndSend(
//                "/topic/session/" + sessionId + "/chat",
//                chat
//        );
    }
}