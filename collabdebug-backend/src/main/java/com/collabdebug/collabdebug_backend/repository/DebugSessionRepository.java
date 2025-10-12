package com.collabdebug.collabdebug_backend.repository;
import com.collabdebug.collabdebug_backend.model.DebugSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DebugSessionRepository extends JpaRepository<DebugSession, UUID> {

    // Find all active sessions to list them on the main page
    List<DebugSession> findAllByIsActiveTrueOrderByCreatedAtDesc();
}