package com.collabdebug.collabdebug_backend.controller;


import com.collabdebug.collabdebug_backend.dto.CreateSessionRequest;
import com.collabdebug.collabdebug_backend.model.DebugSession;
import com.collabdebug.collabdebug_backend.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
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
            Authentication authentication // Spring Security automatically provides this
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

    // POST /api/sessions/end/{sessionId}
    @PostMapping("/end/{sessionId}")
    public ResponseEntity<Void> endSession(
            @PathVariable UUID sessionId,
            Authentication authentication
    ) {
        sessionService.endSession(sessionId, authentication);
        return ResponseEntity.noContent().build();
    }
}