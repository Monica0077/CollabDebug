package com.collabdebug.collabdebug_backend.controller;

import com.collabdebug.collabdebug_backend.model.SandboxSession;
import com.collabdebug.collabdebug_backend.service.SandboxService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/sandbox")
@CrossOrigin(origins = "http://localhost:5174")
public class SandboxController {

    private final SandboxService sandboxService;

    public SandboxController(SandboxService sandboxService) {
        this.sandboxService = sandboxService;
    }

    @PostMapping("/create")
    public ResponseEntity<SandboxSession> createSandbox(
            @RequestParam("language") String language,
            @RequestParam("code") MultipartFile codeFile,
            Authentication authentication) throws Exception {

        String username = authentication.getName();
        SandboxSession session = sandboxService.createSandbox(username, language, codeFile);
        return ResponseEntity.ok(session);
    }

    @GetMapping("/logs/{containerId}")
    public ResponseEntity<String> getSandboxLogs(@PathVariable String containerId) {
        return ResponseEntity.ok(sandboxService.fetchLogs(containerId));
    }
}
