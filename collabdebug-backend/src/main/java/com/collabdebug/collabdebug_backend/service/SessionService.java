package com.collabdebug.collabdebug_backend.service;

import com.collabdebug.collabdebug_backend.dto.ws.EditMessage;
import com.collabdebug.collabdebug_backend.dto.ws.EditResponse;
import com.collabdebug.collabdebug_backend.model.DebugSession;
import com.collabdebug.collabdebug_backend.redis.RedisPublisher;
import com.collabdebug.collabdebug_backend.repository.DebugSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SessionService {

    private final DebugSessionRepository sessionRepository;
    private final SimpMessagingTemplate msgTemplate;
    public static final String CHAT_REDIS_TOPIC_PREFIX = "chat-updates:";
    public static final String TERMINAL_REDIS_TOPIC_PREFIX = "terminal-updates:";
    private final RedisPublisher redisPublisher;

    // Docker container mapping: sessionId -> containerName
    private final Map<UUID, String> sessionDockerMap = new ConcurrentHashMap<>();

    // WebSocket session tracking: sessionId -> connectionIds
    private final Map<String, Set<String>> sessionToLocalConnectionIds = new ConcurrentHashMap<>();

    // Document state and versioning: sessionId -> code / version
    private final Map<String, String> documentMaster = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> serverVersion = new ConcurrentHashMap<>();

    @Autowired
    public SessionService(DebugSessionRepository sessionRepository, SimpMessagingTemplate msgTemplate, RedisPublisher redisPublisher) {
        this.sessionRepository = sessionRepository;
        this.msgTemplate = msgTemplate;
        this.redisPublisher = redisPublisher;
    }

    // ------------------- Session Management -------------------
    // ... (createSession, listActiveSessions, joinSession, leaveSession, endSession remain unchanged)

    @Transactional
    public DebugSession createSession(String sessionName, Authentication authentication) {
        String ownerUsername = authentication.getName();
        DebugSession session = new DebugSession();
        session.setName(sessionName);
        session.setOwnerUsername(ownerUsername);
        session.setActive(true);
        session.setLatestCode(""); // initial empty code
        return sessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public List<DebugSession> listActiveSessions() {
        return sessionRepository.findAllByIsActiveTrueOrderByCreatedAtDesc();
    }

    @Transactional
    public DebugSession joinSession(UUID sessionId, Authentication authentication) {
        DebugSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (!session.isActive()) {
            throw new RuntimeException("Cannot join an inactive session.");
        }

        String username = authentication.getName();
        session.setCurrentUser(username);
        // Only add if not already present
        if (!session.getParticipants().contains(username)) {
            session.getParticipants().add(username);
        }
        sessionRepository.save(session); // persist

        return session;
    }

    @Transactional
    public void leaveSession(UUID sessionId, Authentication auth) {
        DebugSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        String username = auth.getName();
        session.getParticipants().remove(username);
        sessionRepository.save(session);

        // stop container if no participants remain
        if (session.getParticipants().isEmpty()) {
            stopContainer(sessionId);
        }
    }

    @Transactional
    public void endSession(UUID sessionId, Authentication auth, boolean saveCode) {
        DebugSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (saveCode) {
            // persist final code to DB (already updated in documentMaster)
            String finalCode = documentMaster.getOrDefault(sessionId.toString(), session.getLatestCode());
            session.setLatestCode(finalCode);
        }

        String containerName = sessionDockerMap.remove(sessionId);
        if (containerName != null) removeDockerContainer(containerName);

        session.setActive(false);
        sessionRepository.save(session);
    }

    // ------------------- Docker Management -------------------

    /**
     * @param containerName The name of the container to check.
     * @return true if the container exists (running or stopped), false otherwise.
     */
    private boolean checkContainerExists(String containerName) throws Exception {
        // 1. Build the process to inspect the container by name
        Process process = new ProcessBuilder("docker", "inspect", containerName).start();

        // 2. Consume the streams to prevent blocking
        // We don't care about the content, only that the buffers are cleared.
        // Reading all bytes ensures the process isn't waiting for the Java thread.
        String output = new String(process.getInputStream().readAllBytes()).trim();
        String error = new String(process.getErrorStream().readAllBytes()).trim();

        // 3. Wait for the process to complete and get the exit code
        int exitCode = process.waitFor();

        // Log the result for debugging
        System.out.println("Docker Inspect for " + containerName + " finished with exit code: " + exitCode);
        if (exitCode != 0) {
            System.err.println("Docker Inspect Error Output: " + error);
        }

        // 4. A zero exit code means the container was found (exists).
        return exitCode == 0;
    }

    private boolean isContainerRunning(String containerName) throws Exception {
        Process process = new ProcessBuilder("docker", "inspect", "-f", "{{.State.Running}}", containerName).start();
        process.waitFor();
        String output = new String(process.getInputStream().readAllBytes()).trim();
        return output.equals("true");
    }

    private void startDockerContainer(String containerName) throws Exception {
        new ProcessBuilder("docker", "start", containerName).start().waitFor();
    }

    private void createDockerContainer(String containerName, String language) throws Exception {
        String imageName = switch (language.toLowerCase()) {
            case "java" -> "openjdk:20-jdk";
            case "python" -> "python:3.12";
            case "javascript" -> "node:20";
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };

        // If 'docker run' with --name fails because the name exists, it throws the exception we want to avoid.
        Process process = new ProcessBuilder(
                "docker", "run", "-dit", "--name", containerName, imageName, "tail", "-f", "/dev/null"
        ).start();

        // Check the exit code of the 'docker run' command
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String error = new String(process.getErrorStream().readAllBytes()).trim();

            // 🚨 Defensive check for the "name already in use" error
            if (error.contains("already in use")) {
                // Throw a custom exception to be handled by the caller, or throw a more generic error
                // For now, let's re-throw the original error to be caught below, or just rely on 'checkContainerExists'
                throw new RuntimeException("Failed to create Docker container: " + containerName + ". Error: " + error);
            }
            throw new RuntimeException("Failed to create Docker container: " + containerName + ". Error: " + error);
        }
    }

    private void broadcastTerminalOutput(UUID sessionId, String output) {
        redisPublisher.publishTerminalOutput(sessionId.toString(), output);
    }

    // 🚨 FIX: Combined defensive logic to handle container reuse after restart
    public String runCodeInDocker(UUID sessionId, String language, String code) throws Exception {
        DebugSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        // 1. Save state (Correct)
        session.setLatestCode(code);
        session.setLanguage(language);
        sessionRepository.save(session);
        documentMaster.put(sessionId.toString(), code);

        String containerName = sessionDockerMap.get(sessionId);
        String requiredName = "session_" + sessionId.toString().replace("-", "");

        // 2. Check in-memory map first
        if (containerName == null) {
            containerName = requiredName;

            // 🚨 CORE FIX: Check Docker's persistent state
            if (checkContainerExists(containerName)) {
                // Container exists in Docker (running or stopped).
                System.out.println("Container " + containerName + " found in Docker. Reusing it.");

                // Re-map it in the backend's memory
                sessionDockerMap.put(sessionId, containerName);
            } else {
                // Container does not exist in Docker or memory: Create it fresh
                System.out.println("Container " + containerName + " not found. Creating new container.");

                // createDockerContainer now throws a RuntimeException on failure
                createDockerContainer(containerName, language);
                sessionDockerMap.put(sessionId, containerName);
            }
        }

        // 3. Ensure the container is running before executing code
        if (!isContainerRunning(containerName)) {
            System.out.println("Container " + containerName + " is not running. Starting it.");
            startDockerContainer(containerName);
        }

        // 4. Execute the code (The final step)
        String output = executeCodeInContainer(containerName, language, code);

        // 5. Broadcast the terminal output after execution
        broadcastTerminalOutput(sessionId, output);

        // 6. Return the output to the caller (e.g., REST controller)
        return output;
    }

    public void stopContainer(UUID sessionId) {
        String containerName = sessionDockerMap.get(sessionId);
        if (containerName != null) {
            try { new ProcessBuilder("docker", "stop", containerName).start().waitFor(); }
            catch (Exception e) { e.printStackTrace(); }
        }
    }

    private String executeCodeInContainer(String containerName, String language, String code) throws Exception {
        String fileName;
        String command;
        switch (language.toLowerCase()) {
            case "java" -> { fileName = "/tmp/Main.java"; command = "javac " + fileName + " && java -cp /tmp Main"; }
            case "python" -> { fileName = "/tmp/code.py"; command = "python " + fileName; }
            case "javascript" -> { fileName = "/tmp/code.js"; command = "node " + fileName; }
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        }

        Process writeProcess = new ProcessBuilder(
                "docker", "exec", "-i", containerName, "bash", "-c", "cat > " + fileName
        ).start();
        writeProcess.getOutputStream().write(code.getBytes());
        writeProcess.getOutputStream().close();
        writeProcess.waitFor();

        Process execProcess = new ProcessBuilder("docker", "exec", containerName, "bash", "-c", command).start();
        String output = new String(execProcess.getInputStream().readAllBytes());
        String error = new String(execProcess.getErrorStream().readAllBytes());
        execProcess.waitFor();

        if (!error.isEmpty()) output += "\nERROR:\n" + error;

        return output;
    }

    private void removeDockerContainer(String containerName) {
        try { new ProcessBuilder("docker", "rm", "-f", containerName).start().waitFor(); }
        catch (Exception e) { e.printStackTrace(); }
    }

    // ------------------- WebSocket Collaboration -------------------
    // ... (userJoined, userLeft, applyEdit, replyToUser remain unchanged)

    public void userJoined(String sessionId, String connectionId, String userId) {
        sessionToLocalConnectionIds.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(connectionId);
        redisPublisher.publishPresence(sessionId,
                Map.of("type", "joined", "userId", userId));
    }

    public void userLeft(String sessionId, String connectionId, String userId) {
        Set<String> set = sessionToLocalConnectionIds.get(sessionId);
        if (set != null) set.remove(connectionId);
        redisPublisher.publishPresence(sessionId,
                Map.of("type", "left", "userId", userId));
    }

    // ------------------- Document Editing -------------------

    /**
     * Applies the edit message by performing a NAIVE full-text replacement,
     * matching the data structure sent by the React client.
     */
    public synchronized EditResponse applyEdit(EditMessage edit) {
        String sid = edit.sessionId;
        documentMaster.putIfAbsent(sid, "");
        serverVersion.putIfAbsent(sid, new AtomicLong(0));

        // 🚨 FIX 1: Handle potential null op or null text defensively
        if (edit.op == null || edit.op.text == null) {
            System.err.println("WARN: Received edit with null op or null text for session " + sid);
            // Rejects the edit but returns current state
            return new EditResponse(false, documentMaster.get(sid), serverVersion.get(sid).get());
        }

        long currentVersion = serverVersion.get(sid).get();

        // NOTE: Client version is currently ignored in this naive replacement logic
        // if (edit.clientVersion != currentVersion) {
        //     return new EditResponse(false, documentMaster.get(sid), currentVersion);
        // }

        // 🚨 FIX 2: Use the full text from the op payload as the updated document.
        String updatedDoc = edit.op.text;

        documentMaster.put(sid, updatedDoc);
        serverVersion.get(sid).incrementAndGet();

        // persist updated code to DB
        UUID sessionUUID = UUID.fromString(sid);
        sessionRepository.findById(sessionUUID).ifPresent(session -> {
            session.setLatestCode(updatedDoc);
            sessionRepository.save(session);
        });

        // broadcast to WebSocket clients
        edit.serverVersion = serverVersion.get(sid).get();
        // 🚨 CRITICAL: Re-add the edit.op.text, as the backend only sends the op object
        // and the frontend needs the code to update the editor.
        // We set the code field in the response for the naive client to use.
        EditResponse response = new EditResponse(true, updatedDoc, edit.serverVersion);

        // 🚨 FIX 3: Broadcast the original edit message so other clients can update.
        // The original EditMessage contains the client userId which is needed for the
        // other clients to ignore their own edits.
        msgTemplate.convertAndSend("/topic/session/" + sid + "/edits", edit);

        // Return the response object (not the broadcast message)
        return response;
    }


    public void replyToUser(String userId, String destination, Object payload) {
        // Sends message to the user-specific queue
        msgTemplate.convertAndSendToUser(userId, destination, payload);
    }
}