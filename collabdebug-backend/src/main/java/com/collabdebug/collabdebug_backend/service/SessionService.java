package com.collabdebug.collabdebug_backend.service;

import com.collabdebug.collabdebug_backend.model.DebugSession;
import com.collabdebug.collabdebug_backend.repository.DebugSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionService {

    @Autowired
    private final DebugSessionRepository sessionRepository;

    // Maps sessionId -> Docker container name
    private final Map<UUID, String> sessionDockerMap = new ConcurrentHashMap<>();

    public SessionService(DebugSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    // --- Session Creation ---
    @Transactional
    public DebugSession createSession(String sessionName, Authentication authentication) {
        String ownerUsername = authentication.getName();

        DebugSession session = new DebugSession();
        session.setName(sessionName);
        session.setOwnerUsername(ownerUsername);
        session.setActive(true);

        DebugSession savedSession = sessionRepository.save(session);
        return savedSession;
    }

    // --- List active sessions ---
    @Transactional(readOnly = true)
    public List<DebugSession> listActiveSessions() {
        return sessionRepository.findAllByIsActiveTrueOrderByCreatedAtDesc();
    }

    // --- Join a session ---
    @Transactional
    public DebugSession joinSession(UUID sessionId, Authentication authentication) {
        DebugSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (!session.isActive()) {
            throw new RuntimeException("Cannot join an inactive session.");
        }

        String username = authentication.getName();
        session.setCurrentUser(username);
        session.getParticipants().add(username);
        sessionRepository.save(session); // persist

        return session;
    }

    // --- Leave a session ---
    @Transactional
    public void leaveSession(UUID sessionId, Authentication auth) {
        DebugSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        String username = auth.getName();
        session.getParticipants().remove(username);
        sessionRepository.save(session);

        // Optionally: stop container if no participants remain
        if (session.getParticipants().isEmpty()) {
            stopContainer(sessionId);
        }
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
    // --- Run code inside Docker container ---
    public String runCodeInDocker(UUID sessionId, String language, String code) throws Exception {
        DebugSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        // Save latest code to DB
        session.setLatestCode(code);
        session.setLanguage(language);
        sessionRepository.save(session);

        String containerName = sessionDockerMap.get(sessionId);

        // If container exists but is stopped, restart it
        if (containerName != null && !isContainerRunning(containerName)) {
            startDockerContainer(containerName);
        }

        // If container does not exist, create it
        if (containerName == null) {
            containerName = "session_" + sessionId.toString().replace("-", "");
            createDockerContainer(containerName, language);
            sessionDockerMap.put(sessionId, containerName);
        }

        return executeCodeInContainer(containerName, language, code);
    }


    // --- Stop container ---
    public void stopContainer(UUID sessionId) {
        String containerName = sessionDockerMap.get(sessionId);
        if (containerName != null) {
            try {
                new ProcessBuilder("docker", "stop", containerName).start().waitFor();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // --- End session ---
    @Transactional
    public void endSession(UUID sessionId, Authentication auth, boolean saveCode) {
        DebugSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (saveCode) {
            // TODO: save current code snapshot to DB
        }

        // Stop and remove container
        String containerName = sessionDockerMap.remove(sessionId);
        if (containerName != null) removeDockerContainer(containerName);

        session.setActive(false);
        sessionRepository.save(session);
    }

    // --- Docker helpers ---
    private void createDockerContainer(String containerName, String language) throws Exception {
        String imageName = switch (language.toLowerCase()) {
            case "java" -> "openjdk:20-jdk";
            case "python" -> "python:3.12";
            case "javascript" -> "node:20";
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };

        ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "-dit", "--name", containerName, imageName, "tail", "-f", "/dev/null"
        );
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) throw new RuntimeException("Failed to create Docker container: " + containerName);
    }

    private String executeCodeInContainer(String containerName, String language, String code) throws Exception {
        String fileName;
        String command;

        switch(language.toLowerCase()) {
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
        try {
            new ProcessBuilder("docker", "rm", "-f", containerName).start().waitFor();
        } catch(Exception e) { e.printStackTrace(); }
    }
}
