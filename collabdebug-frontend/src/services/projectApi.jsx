// src/services/projectApi.jsx
import API from "../api/api";

const API_BASE_PATH = '/api/projects';

/**
 * Create a new project
 * POST /api/projects
 */
export const createProject = async (name, language, code = "", description = "") => {
  const response = await API.post(API_BASE_PATH, {
    name,
    language,
    code,
    description
  });
  return response.data;
};

/**
 * Get all projects
 * GET /api/projects
 */
export const getAllProjects = async () => {
  const response = await API.get(API_BASE_PATH);
  return response.data;
};

/**
 * Get projects by owner username
 * GET /api/projects/user/{username}
 */
export const getProjectsByOwner = async (username) => {
  const response = await API.get(`${API_BASE_PATH}/user/${username}`);
  return response.data;
};

/**
 * Get a single project by ID
 * GET /api/projects/{projectId}
 */
export const getProjectById = async (projectId) => {
  const response = await API.get(`${API_BASE_PATH}/${projectId}`);
  return response.data;
};

/**
 * Update a project
 * PUT /api/projects/{projectId}
 */
export const updateProject = async (projectId, name, language, code, description = "") => {
  const response = await API.put(`${API_BASE_PATH}/${projectId}`, {
    name,
    language,
    code,
    description
  });
  return response.data;
};

/**
 * Delete a project
 * DELETE /api/projects/{projectId}
 */
export const deleteProject = async (projectId) => {
  await API.delete(`${API_BASE_PATH}/${projectId}`);
};

/**
 * Create a session from a project
 * POST /api/projects/{projectId}/session
 */
export const createSessionFromProject = async (projectId, sessionName = null) => {
  const response = await API.post(`${API_BASE_PATH}/${projectId}/session`, {
    sessionName
  });
  return response.data;
};
