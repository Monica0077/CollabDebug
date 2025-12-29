import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { createProject, getAllProjects, deleteProject } from '../services/projectApi';
import { createSession, joinSession } from '../services/sessionApi';
import StartSessionModal from './StartSessionModal';
import { FaTrash, FaPlay, FaSpinner, FaPlus } from 'react-icons/fa';
import '../ExploreProjects.css';

const ExploreProjects = () => {
  const [projects, setProjects] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showUploadModal, setShowUploadModal] = useState(false);
  const [selectedProject, setSelectedProject] = useState(null);
  const [showStartSessionModal, setShowStartSessionModal] = useState(false);
  const [uploadData, setUploadData] = useState({
    name: '',
    language: 'java',
    code: '',
    description: ''
  });
  const [isUploading, setIsUploading] = useState(false);
  const navigate = useNavigate();

  const fetchProjects = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await getAllProjects();
      setProjects(data);
    } catch (err) {
      console.error("Failed to fetch projects:", err);
      setError("Failed to load projects. Please try again.");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchProjects();
  }, []);

  const handleUploadProject = async (e) => {
    e.preventDefault();
    setError(null);
    setIsUploading(true);

    if (!uploadData.name.trim()) {
      setError("Project name cannot be empty.");
      setIsUploading(false);
      return;
    }

    if (!uploadData.code.trim()) {
      setError("Project code cannot be empty.");
      setIsUploading(false);
      return;
    }

    try {
      const newProject = await createProject(
        uploadData.name,
        uploadData.language,
        uploadData.code,
        uploadData.description
      );
      
      // Add new project to the list
      setProjects([newProject, ...projects]);
      
      // Reset form and close modal
      setUploadData({
        name: '',
        language: 'java',
        code: '',
        description: ''
      });
      setShowUploadModal(false);
      
      alert(`Project "${newProject.name}" uploaded successfully!`);
    } catch (err) {
      console.error("Upload failed:", err);
      setError(err.response?.data || "Failed to upload project. Please try again.");
    } finally {
      setIsUploading(false);
    }
  };

  const handleDeleteProject = async (projectId, projectName) => {
    if (!window.confirm(`Are you sure you want to delete "${projectName}"?`)) {
      return;
    }

    try {
      await deleteProject(projectId);
      setProjects(projects.filter(p => p.id !== projectId));
      alert(`Project "${projectName}" deleted successfully!`);
    } catch (err) {
      console.error("Delete failed:", err);
      setError(err.response?.data || "Failed to delete project. Please try again.");
    }
  };

  const handleStartSession = async (project) => {
    setSelectedProject(project);
    setShowStartSessionModal(true);
  };

  const handleStartSessionConfirmed = async (sessionName) => {
    try {
      const createdSession = await createSession(sessionName);
      const sessionId = createdSession.id;

      await joinSession(sessionId);

      navigate(`/session/${sessionId}`, {
        state: {
          initialCode: selectedProject.code,
          language: selectedProject.language,
          projectId: selectedProject.id
        }
      });
    } catch (err) {
      console.error("Failed to start session from project:", err);
      throw new Error(err.response?.data || err.message);
    }
  };

  if (isLoading) {
    return (
      <div className="explore-projects-loading">
        <FaSpinner className="spinner" />
        <span>Loading projects...</span>
      </div>
    );
  }

  return (
    <div className="explore-projects-container">
      <div className="explore-projects-header">
        <h2>Explore Projects</h2>
        <button
          className="btn-upload-project"
          onClick={() => setShowUploadModal(true)}
        >
          <FaPlus /> Upload New Project
        </button>
      </div>

      {error && <div className="explore-projects-error">{error}</div>}

      {projects.length === 0 && !isLoading && !error ? (
        <div className="explore-projects-empty">
          <p>No projects available yet. Upload your first project!</p>
        </div>
      ) : (
        <div className="projects-grid">
          {projects.map((project) => (
            <div key={project.id} className="project-card">
              <div className="project-header">
                <h3 className="project-name">{project.name}</h3>
                <span className="project-language">{project.language.toUpperCase()}</span>
              </div>

              {project.description && (
                <p className="project-description">{project.description}</p>
              )}

              <div className="project-meta">
                <p className="project-owner">By: {project.ownerUsername}</p>
                <p className="project-date">
                  {new Date(project.createdAt).toLocaleDateString()}
                </p>
              </div>

              {project.code && (
                <div className="project-code-preview">
                  <pre><code>{project.code.substring(0, 200)}...</code></pre>
                </div>
              )}

              <div className="project-actions">
                <button
                  className="btn-start-session"
                  onClick={() => handleStartSession(project)}
                  title="Start a session with this project"
                >
                  <FaPlay /> Start Session
                </button>
                <button
                  className="btn-delete"
                  onClick={() => handleDeleteProject(project.id, project.name)}
                  title="Delete this project"
                >
                  <FaTrash />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Upload Project Modal */}
      {showUploadModal && (
        <div className="modal-overlay" onClick={() => setShowUploadModal(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <h2>Upload New Project</h2>
            <form onSubmit={handleUploadProject}>
              <div className="form-group">
                <label>Project Name *</label>
                <input
                  type="text"
                  value={uploadData.name}
                  onChange={(e) =>
                    setUploadData({ ...uploadData, name: e.target.value })
                  }
                  placeholder="e.g., Hello World"
                  required
                />
              </div>

              <div className="form-group">
                <label>Programming Language *</label>
                <select
                  value={uploadData.language}
                  onChange={(e) =>
                    setUploadData({ ...uploadData, language: e.target.value })
                  }
                >
                  <option value="java">Java</option>
                  <option value="javascript">JavaScript</option>
                  <option value="python">Python</option>
                  <option value="cpp">C++</option>
                  <option value="csharp">C#</option>
                </select>
              </div>

              <div className="form-group">
                <label>Description (optional)</label>
                <input
                  type="text"
                  value={uploadData.description}
                  onChange={(e) =>
                    setUploadData({ ...uploadData, description: e.target.value })
                  }
                  placeholder="Brief description of your project"
                />
              </div>

              <div className="form-group">
                <label>Code *</label>
                <textarea
                  value={uploadData.code}
                  onChange={(e) =>
                    setUploadData({ ...uploadData, code: e.target.value })
                  }
                  placeholder="Paste your code here..."
                  rows="12"
                  required
                />
              </div>

              {error && <div className="form-error">{error}</div>}

              <div className="form-actions">
                <button
                  type="button"
                  className="btn-cancel"
                  onClick={() => setShowUploadModal(false)}
                  disabled={isUploading}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn-submit"
                  disabled={isUploading}
                >
                  {isUploading ? (
                    <>
                      <FaSpinner className="spinner" /> Uploading...
                    </>
                  ) : (
                    <>
                      <FaPlus /> Upload Project
                    </>
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Start Session Modal */}
      {showStartSessionModal && selectedProject && (
        <StartSessionModal
          project={selectedProject}
          onClose={() => setShowStartSessionModal(false)}
          onStart={handleStartSessionConfirmed}
        />
      )}
    </div>
  );
};

export default ExploreProjects;
