package com.collabdebug.collabdebug_backend.model;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "debug_session")
@Getter
@Setter

public class DebugSession {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String ownerUsername; // Corresponds to the User who created it

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private boolean isActive = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SandboxStatus sandboxStatus = SandboxStatus.NOT_STARTED;

    // Added to store the actual Docker container ID once created
    @Column
    private String sandboxContainerId;

    // Enum for the sandbox status, as defined in our design
    public enum SandboxStatus {
        NOT_STARTED, RUNNING, STOPPED, ERROR
    }

    public DebugSession() {
    }

    public DebugSession(String name, String ownerUsername, LocalDateTime createdAt, boolean isActive, SandboxStatus sandboxStatus, String sandboxContainerId) {
        this.name = name;
        this.ownerUsername = ownerUsername;
        this.createdAt = createdAt;
        this.isActive = isActive;
        this.sandboxStatus = sandboxStatus;
        this.sandboxContainerId = sandboxContainerId;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public SandboxStatus getSandboxStatus() {
        return sandboxStatus;
    }

    public void setSandboxStatus(SandboxStatus sandboxStatus) {
        this.sandboxStatus = sandboxStatus;
    }

    public String getSandboxContainerId() {
        return sandboxContainerId;
    }

    public void setSandboxContainerId(String sandboxContainerId) {
        this.sandboxContainerId = sandboxContainerId;
    }
}