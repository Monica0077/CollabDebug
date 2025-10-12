// src/services/sessionApi.js

import API from "../api/api"; // Your custom, JWT-intercepting axios instance
//import { connectToSessionWebSocket } from './websocketService'; 

// The backend endpoints use /api as a prefix, so we will use the full path relative to the base URL
const API_BASE_PATH = '/api/sessions'; 

/**
 * REST API: Creates a new debugging session on the backend.
 * Uses POST /api/sessions/create
 * @param {string} sessionName 
 * @returns {Promise<object>} The created session object (includes UUID)
 */
export const createSession = async (sessionName) => {
  // The API instance handles the base URL and JWT token
  const response = await API.post(
    `${API_BASE_PATH}/create`,
    { name: sessionName }
  );
  return response.data; 
};

/**
 * REST API: Fetches the list of all active sessions.
 * Uses GET /api/sessions
 * @returns {Promise<Array<object>>} List of active sessions
 */
export const listSessions = async () => {
  const response = await API.get(
    API_BASE_PATH
  );
  return response.data;
};

/**
 * REST API + WebSocket: Joins the session and establishes the real-time connection.
 * Uses POST /api/sessions/join/{sessionId}
 * @param {string} sessionId UUID of the session to join.
 * @returns {Promise<object>} The session object.
 */
export const joinSession = async (sessionId) => {
  // 1. Call REST API to register the user as a participant
  const response = await API.post(
    `${API_BASE_PATH}/join/${sessionId}`
  );
  
  const session = response.data;

  // 2. Establish the WebSocket connection for real-time collaboration
  try {
      console.log(`Attempting to connect WebSocket for session: ${sessionId}`);
      //connectToSessionWebSocket(sessionId); 
  } catch (wsError) {
      console.error('Failed to establish WebSocket connection:', wsError);
      // Fail gracefully here, maybe show a warning to the user
  }

  // 3. Return the session object
  return session;
};

export const endSession = async (sessionId) => {
    // Uses POST /api/sessions/end/{sessionId}
    await API.post(`${API_BASE_PATH}/end/${sessionId}`);
    // No content expected, just clean up client-side state
}