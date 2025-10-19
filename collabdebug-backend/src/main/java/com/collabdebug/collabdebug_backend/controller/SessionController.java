package com.collabdebug.collabdebug_backend.controller;

import com.collabdebug.collabdebug_backend.dto.CreateSessionRequest;
import com.collabdebug.collabdebug_backend.model.DebugSession;
import com.collabdebug.collabdebug_backend.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    // POST /api/sessions/create
    @PostMapping("/create")
    public ResponseEntity<DebugSession> createSession(
            @Valid @RequestBody CreateSessionRequest request,
            Authentication authentication
    ) {
        DebugSession newSession = sessionService.createSession(request.getName(), authentication);
        return ResponseEntity.ok(newSession);
    }

    // GET /api/sessions
    @GetMapping
    public ResponseEntity<List<DebugSession>> listActiveSessions() {
        List<DebugSession> sessions = sessionService.listActiveSessions();
        return ResponseEntity.ok(sessions);
    }

    // POST /api/sessions/join/{sessionId}
    @PostMapping("/join/{sessionId}")
    public ResponseEntity<DebugSession> joinSession(
            @PathVariable UUID sessionId,
            Authentication authentication
    ) {
        DebugSession session = sessionService.joinSession(sessionId, authentication);
        return ResponseEntity.ok(session);
    }

    // POST /api/sessions/leave/{sessionId}
    @PostMapping("/leave/{sessionId}")
    public ResponseEntity<Void> leaveSession(
            @PathVariable UUID sessionId,
            Authentication auth
    ) {
        sessionService.leaveSession(sessionId, auth);
        return ResponseEntity.noContent().build();
    }

    // POST /api/sessions/run/{sessionId}
    @PostMapping("/run/{sessionId}")
    public ResponseEntity<String> runCode(
            @PathVariable UUID sessionId,
            @RequestBody Map<String, String> payload
    ) {
        String language = payload.get("language");
        String code = payload.get("code");
        try {
            String output = sessionService.runCodeInDocker(sessionId, language, code);
            return ResponseEntity.ok(output);
        } catch(Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    // POST /api/sessions/stop/{sessionId}
    @PostMapping("/stop/{sessionId}")
    public ResponseEntity<Void> stopContainer(
            @PathVariable UUID sessionId,
            Authentication auth
    ) {
        sessionService.stopContainer(sessionId);
        return ResponseEntity.noContent().build();
    }

    // POST /api/sessions/end/{sessionId}
    @PostMapping("/end/{sessionId}")
    public ResponseEntity<Void> endSession(
            @PathVariable UUID sessionId,
            @RequestBody Map<String, String> body,
            Authentication auth
    ) {
        // Expecting {"latestCode": "..."}
        String latestCode = body.getOrDefault("latestCode", "");
        sessionService.endSession(sessionId, auth, true); // saveCode = true
        return ResponseEntity.noContent().build();
    }
}
