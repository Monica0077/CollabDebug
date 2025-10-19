import API from '../api/api'; // Your axios instance

export const runCode = async (sessionId, language, code) => {
  const response = await API.post(`/api/sessions/run/${sessionId}`, {
    language,
    code,
  });
  return response.data; // Terminal output from backend
};