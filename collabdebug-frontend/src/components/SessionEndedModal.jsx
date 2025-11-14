import React from 'react';
import '../SessionEndedModal.css';

const SessionEndedModal = ({ title = 'Session Ended', message, onAcknowledge }) => {
  return (
    <div className="session-ended-overlay">
      <div className="session-ended-container">
        <h1>{title}</h1>
        <p>{message}</p>
        <div className="session-ended-actions">
          <button className="session-ended-ok" onClick={onAcknowledge}>OK</button>
        </div>
      </div>
    </div>
  );
};

export default SessionEndedModal;
