
package com.collabdebug.collabdebug_backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
public class SandboxSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String sessionId;

    private String owner;
    private String containerId;
    private String language;
    private String fileName;
    private String status; // e.g., RUNNING, STOPPED, FAILED
    private LocalDateTime createdAt;

    public SandboxSession() {}

    public SandboxSession(String owner, String containerId, String language, String fileName, String status) {
        this.sessionId = UUID.randomUUID().toString();
        this.owner = owner;
        this.containerId = containerId;
        this.language = language;
        this.fileName = fileName;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    // --- Getters and Setters ---
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getContainerId() { return containerId; }
    public void setContainerId(String containerId) { this.containerId = containerId; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
