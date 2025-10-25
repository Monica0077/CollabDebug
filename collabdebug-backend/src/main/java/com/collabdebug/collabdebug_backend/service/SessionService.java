package com.collabdebug.collabdebug_backend.service;

import com.collabdebug.collabdebug_backend.dto.ws.EditMessage;
import com.collabdebug.collabdebug_backend.dto.ws.EditOperation;
import com.collabdebug.collabdebug_backend.dto.ws.EditResponse;
import com.collabdebug.collabdebug_backend.model.DebugSession;
import com.collabdebug.collabdebug_backend.repository.DebugSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SessionService {

    private final DebugSessionRepository sessionRepository;
    private final SimpMessagingTemplate msgTemplate;

    // Docker container mapping: sessionId -> containerName
    private final Map<UUID, String> sessionDockerMap = new ConcurrentHashMap<>();

    // WebSocket session tracking: sessionId -> connectionIds
    private final Map<String, Set<String>> sessionToLocalConnectionIds = new ConcurrentHashMap<>();

    // Document state and versioning: sessionId -> code / version
    private final Map<String, String> documentMaster = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> serverVersion = new ConcurrentHashMap<>();

    @Autowired
    public SessionService(DebugSessionRepository sessionRepository, SimpMessagingTemplate msgTemplate) {
        this.sessionRepository = sessionRepository;
        this.msgTemplate = msgTemplate;
    }

    // ------------------- Session Management -------------------

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

    // (Docker methods remain unchanged)

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

        Process process = new ProcessBuilder(
                "docker", "run", "-dit", "--name", containerName, imageName, "tail", "-f", "/dev/null"
        ).start();
        if (process.waitFor() != 0) throw new RuntimeException("Failed to create Docker container: " + containerName);
    }

    public String runCodeInDocker(UUID sessionId, String language, String code) throws Exception {
        DebugSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        // Save code to DB and memory
        session.setLatestCode(code);
        session.setLanguage(language);
        sessionRepository.save(session);
        documentMaster.put(sessionId.toString(), code);

        String containerName = sessionDockerMap.get(sessionId);

        if (containerName != null && !isContainerRunning(containerName)) {
            startDockerContainer(containerName);
        }

        if (containerName == null) {
            containerName = "session_" + sessionId.toString().replace("-", "");
            createDockerContainer(containerName, language);
            sessionDockerMap.put(sessionId, containerName);
        }

        return executeCodeInContainer(containerName, language, code);
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

    public void userJoined(String sessionId, String connectionId, String userId) {
        sessionToLocalConnectionIds.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(connectionId);
        msgTemplate.convertAndSend("/topic/session/" + sessionId + "/presence",
                Map.of("type", "joined", "userId", userId));
    }

    public void userLeft(String sessionId, String connectionId, String userId) {
        Set<String> set = sessionToLocalConnectionIds.get(sessionId);
        if (set != null) set.remove(connectionId);
        msgTemplate.convertAndSend("/topic/session/" + sessionId + "/presence",
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

    // 🚨 FIX 4: Remove or comment out the problematic range-based function
    // private String applyOperationToText(String doc, EditOperation op) {
    //     // THIS WAS THE CRASH POINT: op.rangeStart and op.rangeEnd were NULL.
    //     int start = Integer.parseInt(op.rangeStart);
    //     int end = Integer.parseInt(op.rangeEnd);
    //     String before = doc.substring(0, start);
    //     String after = doc.substring(end);
    //     return before + (op.text == null ? "" : op.text) + after;
    // }

    public void replyToUser(String userId, String destination, Object payload) {
        // Sends message to the user-specific queue
        msgTemplate.convertAndSendToUser(userId, destination, payload);
    }
}