import React, { useState } from 'react';
import { FaPlay, FaSpinner, FaTimes } from 'react-icons/fa';
import '../StartSessionModal.css';

const StartSessionModal = ({ project, onClose, onStart }) => {
  const [sessionName, setSessionName] = useState(`${project.name} - Session`);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);

    if (!sessionName.trim()) {
      setError('Session name cannot be empty.');
      return;
    }

    setIsLoading(true);
    try {
      await onStart(sessionName);
    } catch (err) {
      console.error('Failed to start session:', err);
      setError(err.message || 'Failed to start session. Please try again.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content session-modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h2>Start Session from Project</h2>
          <button className="close-button" onClick={onClose} disabled={isLoading}>
            <FaTimes />
          </button>
        </div>

        <div className="modal-body">
          <p className="project-info">
            <strong>Project:</strong> {project.name}
            <span className="language-badge">{project.language.toUpperCase()}</span>
          </p>

          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label htmlFor="sessionName">Session Name *</label>
              <input
                id="sessionName"
                type="text"
                value={sessionName}
                onChange={(e) => setSessionName(e.target.value)}
                placeholder="Enter session name..."
                disabled={isLoading}
                autoFocus
              />
              <p className="form-hint">
                This is the name other users will see in the Active Collaboration Rooms list.
              </p>
            </div>

            {error && <div className="form-error">{error}</div>}

            <div className="modal-actions">
              <button
                type="button"
                className="btn-cancel"
                onClick={onClose}
                disabled={isLoading}
              >
                Cancel
              </button>
              <button
                type="submit"
                className="btn-start"
                disabled={isLoading}
              >
                {isLoading ? (
                  <>
                    <FaSpinner className="spinner" /> Starting...
                  </>
                ) : (
                  <>
                    <FaPlay /> Start Session
                  </>
                )}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
};

export default StartSessionModal;
