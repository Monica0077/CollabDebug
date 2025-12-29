package com.collabdebug.collabdebug_backend.service;

import com.collabdebug.collabdebug_backend.model.Project;
import com.collabdebug.collabdebug_backend.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    @Autowired
    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    /**
     * Create a new project
     */
    @Transactional
    public Project createProject(String name, String language, String code, String description, Authentication authentication) {
        String username = authentication.getName();
        Project project = new Project(name, language, username, code);
        if (description != null) {
            project.setDescription(description);
        }
        return projectRepository.save(project);
    }

    /**
     * Get all projects
     */
    @Transactional(readOnly = true)
    public List<Project> getAllProjects() {
        return projectRepository.findAll();
    }

    /**
     * Get projects by owner
     */
    @Transactional(readOnly = true)
    public List<Project> getProjectsByOwner(String username) {
        return projectRepository.findByOwnerUsername(username);
    }

    /**
     * Get a single project by ID
     */
    @Transactional(readOnly = true)
    public Project getProjectById(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));
    }

    /**
     * Update a project
     */
    @Transactional
    public Project updateProject(UUID projectId, String name, String language, String code, String description, Authentication authentication) {
        String username = authentication.getName();
        Project project = getProjectById(projectId);
        
        // Check if user is owner
        if (!project.getOwnerUsername().equals(username)) {
            throw new RuntimeException("You do not have permission to update this project");
        }

        project.setName(name);
        project.setLanguage(language);
        project.setCode(code);
        if (description != null) {
            project.setDescription(description);
        }
        return projectRepository.save(project);
    }

    /**
     * Delete a project
     */
    @Transactional
    public void deleteProject(UUID projectId, Authentication authentication) {
        String username = authentication.getName();
        Project project = getProjectById(projectId);
        
        // Check if user is owner
        if (!project.getOwnerUsername().equals(username)) {
            throw new RuntimeException("You do not have permission to delete this project");
        }

        projectRepository.deleteById(projectId);
    }
}
