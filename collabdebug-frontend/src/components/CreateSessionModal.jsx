import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { createSession, joinSession } from '../services/sessionApi';
import '../CreateSessionModal.css'; // import CSS

const CreateSessionModal = ({ onClose, onSuccess }) => {
  const [sessionName, setSessionName] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setIsLoading(true);

    if (!sessionName.trim()) {
      setError("Session name cannot be empty.");
      setIsLoading(false);
      return;
    }

    try {
      const createdSession = await createSession(sessionName);
      const sessionId = createdSession.id;

      await joinSession(sessionId);

      onClose();
      onSuccess(createdSession.name);
      navigate(`/session/${sessionId}`); 
    } catch (err) {
      console.error("Session creation failed:", err);
      setError(`Failed to create session: ${err.response?.data || err.message}`);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="modal-overlay">
      <div className="modal-container">
        <h2 className="modal-title">Create a New Debug Session</h2>
        
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="sessionName" className="form-label">
              Session Name
            </label>
            <input
              type="text"
              id="sessionName"
              value={sessionName}
              onChange={(e) => setSessionName(e.target.value)}
              className="form-input"
              placeholder="e.g., Spring Security Debugging"
              required
              disabled={isLoading}
            />
          </div>
          
          {error && (
            <div className="alert-error" role="alert">
              <p>{error}</p>
            </div>
          )}

          <div className="button-group">
            <button
              type="button"
              onClick={onClose}
              className="btn-cancel"
              disabled={isLoading}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="btn-submit"
              disabled={isLoading}
            >
              {isLoading ? 'Creating...' : 'Create & Join Session'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default CreateSessionModal;
