// src/components/ConfirmationModal.jsx
import React from 'react';
import '../ConfirmationModal.css';

const ConfirmationModal = ({ title, message, onConfirm, onCancel }) => {
  return (
    <div className="modal-overlay">
      <div className="modal-container">
        <h2>{title}</h2>
        <p>{message}</p>
        <div className="modal-actions">
          <button onClick={onCancel} className="btn-cancel">Cancel</button>
          <button onClick={onConfirm} className="btn-danger">End Session</button>
        </div>
      </div>
    </div>
  );
};

export default ConfirmationModal;
