package com.collabdebug.collabdebug_backend.service;


import com.collabdebug.collabdebug_backend.model.DebugSession;
import com.collabdebug.collabdebug_backend.repository.DebugSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class SessionService {
    @Autowired
    private final DebugSessionRepository sessionRepository;

    public SessionService(DebugSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }
    // We will add Redis and WebSocket dependencies in Phase 2

    @Transactional
    public DebugSession createSession(String sessionName, Authentication authentication) {
        // The owner's username is extracted from the JWT/SecurityContext
        String ownerUsername = authentication.getName();

        DebugSession session = new DebugSession();
        session.setName(sessionName);
        session.setOwnerUsername(ownerUsername);

        // Save to PostgreSQL
        DebugSession savedSession = sessionRepository.save(session);

        // TODO: Phase 2 - Add session to Redis for fast lookup

        return savedSession;
    }

    public List<DebugSession> listActiveSessions() {
        return sessionRepository.findAllByIsActiveTrueOrderByCreatedAtDesc();
    }

    @Transactional
    public DebugSession joinSession(UUID sessionId, Authentication authentication) {
        // Basic check: is the session active?
        DebugSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found with ID: " + sessionId));

        if (!session.isActive()) {
            throw new RuntimeException("Cannot join an inactive session.");
        }

        String username = authentication.getName();

        // TODO: Phase 2 - Implement Participant entity/table logic here
        // TODO: Phase 2 - WebSocket broadcast: User X joined the session

        return session;
    }

    @Transactional
    public void endSession(UUID sessionId, Authentication authentication) {
        DebugSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found with ID: " + sessionId));

        // Security check: Only the owner or an Admin can end a session
        if (!session.getOwnerUsername().equals(authentication.getName())) {
            // throw new AccessDeniedException("Only the owner can end the session.");
            // For now, let's keep it simple
            throw new RuntimeException("You are not the owner of this session.");
        }

        session.setActive(false);
        sessionRepository.save(session);

        // TODO: Phase 3 - Cleanup: Call SandboxService to stop and remove Docker container
        // TODO: Phase 2 - WebSocket broadcast: Session has been closed
    }
}