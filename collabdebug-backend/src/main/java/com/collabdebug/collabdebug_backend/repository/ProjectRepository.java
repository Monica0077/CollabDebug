package com.collabdebug.collabdebug_backend.repository;

import com.collabdebug.collabdebug_backend.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findByOwnerUsername(String ownerUsername);
    List<Project> findAll();
}
