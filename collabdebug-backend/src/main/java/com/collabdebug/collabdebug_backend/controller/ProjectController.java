package com.collabdebug.collabdebug_backend.controller;

import com.collabdebug.collabdebug_backend.model.Project;
import com.collabdebug.collabdebug_backend.service.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
@CrossOrigin(origins = "http://localhost:5174")
public class ProjectController {

    private final ProjectService projectService;

    @Autowired
    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * Create a new project
     * POST /api/projects
     */
    @PostMapping
    public ResponseEntity<Project> createProject(
            @RequestBody Map<String, String> payload,
            Authentication authentication
    ) {
        String name = payload.get("name");
        String language = payload.get("language");
        String code = payload.getOrDefault("code", "");
        String description = payload.get("description");

        if (name == null || language == null) {
            return ResponseEntity.badRequest().build();
        }

        Project project = projectService.createProject(name, language, code, description, authentication);
        return ResponseEntity.ok(project);
    }

    /**
     * Get all projects
     * GET /api/projects
     */
    @GetMapping
    public ResponseEntity<List<Project>> getAllProjects() {
        List<Project> projects = projectService.getAllProjects();
        return ResponseEntity.ok(projects);
    }

    /**
     * Get projects by owner
     * GET /api/projects/user/{username}
     */
    @GetMapping("/user/{username}")
    public ResponseEntity<List<Project>> getProjectsByOwner(@PathVariable String username) {
        List<Project> projects = projectService.getProjectsByOwner(username);
        return ResponseEntity.ok(projects);
    }

    /**
     * Get a single project by ID
     * GET /api/projects/{projectId}
     */
    @GetMapping("/{projectId}")
    public ResponseEntity<Project> getProjectById(@PathVariable UUID projectId) {
        Project project = projectService.getProjectById(projectId);
        return ResponseEntity.ok(project);
    }

    /**
     * Update a project
     * PUT /api/projects/{projectId}
     */
    @PutMapping("/{projectId}")
    public ResponseEntity<Project> updateProject(
            @PathVariable UUID projectId,
            @RequestBody Map<String, String> payload,
            Authentication authentication
    ) {
        String name = payload.get("name");
        String language = payload.get("language");
        String code = payload.get("code");
        String description = payload.get("description");

        Project project = projectService.updateProject(projectId, name, language, code, description, authentication);
        return ResponseEntity.ok(project);
    }

    /**
     * Delete a project
     * DELETE /api/projects/{projectId}
     */
    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(
            @PathVariable UUID projectId,
            Authentication authentication
    ) {
        projectService.deleteProject(projectId, authentication);
        return ResponseEntity.noContent().build();
    }

    /**
     * Create a session from a project
     * POST /api/projects/{projectId}/session
     */
    @PostMapping("/{projectId}/session")
    public ResponseEntity<Map<String, String>> createSessionFromProject(
            @PathVariable UUID projectId,
            @RequestBody Map<String, String> payload,
            Authentication authentication
    ) {
        String sessionName = payload.getOrDefault("sessionName", null);
        Project project = projectService.getProjectById(projectId);

        if (sessionName == null) {
            sessionName = project.getName() + " - Session";
        }

        // Return response with project details and session name
        return ResponseEntity.ok(Map.of(
                "sessionName", sessionName,
                "language", project.getLanguage(),
                "code", project.getCode(),
                "projectId", project.getId().toString()
        ));
    }
}
