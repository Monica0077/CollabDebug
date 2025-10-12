package com.collabdebug.collabdebug_backend.service;

import com.collabdebug.collabdebug_backend.model.SandboxSession;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.core.command.PullImageResultCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.UUID;

@Service
public class SandboxService {

    private final DockerClient dockerClient;
    private final Path sandboxBasePath = Paths.get("./sandboxes").toAbsolutePath().normalize();

    public SandboxService() {
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        var httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .connectionTimeout(Duration.ofSeconds(30))
                .build();

        this.dockerClient = DockerClientBuilder.getInstance(config)
                .withDockerHttpClient(httpClient)
                .build();
    }

    public SandboxSession createSandbox(String username, String language, MultipartFile codeFile) throws Exception {
        String sandboxId = UUID.randomUUID().toString();
        Path sandboxPath = sandboxBasePath.resolve(sandboxId);
        Files.createDirectories(sandboxPath);

        String fileName = StringUtils.cleanPath(codeFile.getOriginalFilename());
        File savedFile = sandboxPath.resolve(fileName).toFile();
        codeFile.transferTo(savedFile);

        String dockerImage = switch (language.toLowerCase()) {
            case "python" -> "python:3.11-slim";
            case "java" -> "openjdk:21-slim";
            case "node" -> "node:20-slim";
            default -> throw new RuntimeException("Unsupported language: " + language);
        };

        // Pull or ensure image
        try {
            dockerClient.pullImageCmd(dockerImage)
                    .exec(new PullImageResultCallback())
                    .awaitCompletion();
        } catch (NotFoundException e) {
            throw new RuntimeException("Docker image not found: " + dockerImage, e);
        }

        var container = dockerClient.createContainerCmd(dockerImage)
                .withName("sandbox-" + sandboxId)
                .withBinds(new Bind(sandboxPath.toAbsolutePath().toString(), new Volume("/sandbox")))
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withTty(true)
                .withCmd(switch (language.toLowerCase()) {
                    case "python" -> new String[]{"python", "/sandbox/" + fileName};
                    case "java" -> new String[]{"java", "/sandbox/" + fileName};
                    case "node" -> new String[]{"node", "/sandbox/" + fileName};
                    default -> new String[]{};
                })
                .exec();

        dockerClient.startContainerCmd(container.getId()).exec();

        return new SandboxSession(username, container.getId(), language, fileName, "RUNNING");
    }

    public String fetchLogs(String containerId) {
        try {
            var logCallback = new com.github.dockerjava.core.command.LogContainerResultCallback();
            dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .exec(logCallback)
                    .awaitCompletion();
            return logCallback.toString();
        } catch (Exception e) {
            return "Error fetching logs: " + e.getMessage();
        }
    }
}
