// src/services/sessionLifecycleApi.js
import API from '../api/api';
const API_BASE_PATH = '/api/sessions'; 
export const stopContainer = async (sessionId) => {
  return API.post(`${API_BASE_PATH}/stop/${sessionId}`);
};

export const leaveSession = async (sessionId) => {
  return API.post(`${API_BASE_PATH}/leave/${sessionId}`);
};

export const endSession = async (sessionId, body) => {
  return API.post(`${API_BASE_PATH}/end/${sessionId}`, body);
};