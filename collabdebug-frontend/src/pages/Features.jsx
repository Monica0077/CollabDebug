import React from 'react';
import './SharedPages.css';

export default function Features() {
  return (
    <div className="page-container">
      <section className="hero-section">
        <h1 className="hero-title">Powerful Features</h1>
        <p className="hero-subtitle">
          Discover the tools that make CollabDebug the ultimate collaborative debugging platform
        </p>
      </section>

      <div className="feature-grid">
        <div className="feature-card">
          <div className="feature-icon">🔐</div>
          <h3 className="feature-title">Secure Sandbox Environment</h3>
          <p className="feature-description">
            Run code in completely isolated environments with robust security measures.
            Each sandbox is containerized to ensure safe execution.
          </p>
        </div>

        <div className="feature-card">
          <div className="feature-icon">🤝</div>
          <h3 className="feature-title">Real-time Collaboration</h3>
          <p className="feature-description">
            Work together with team members in real-time. Share sessions, chat,
            and debug simultaneously with multiple participants.
          </p>
        </div>

        <div className="feature-card">
          <div className="feature-icon">🔑</div>
          <h3 className="feature-title">JWT Authentication</h3>
          <p className="feature-description">
            Enterprise-grade security with JSON Web Token authentication.
            Keep your debugging sessions private and secure.
          </p>
        </div>

        <div className="feature-card">
          <div className="feature-icon">💬</div>
          <h3 className="feature-title">Integrated Chat</h3>
          <p className="feature-description">
            Discuss issues and share insights with built-in chat functionality.
            Keep all communication in one place.
          </p>
        </div>

        <div className="feature-card">
          <div className="feature-icon">📊</div>
          <h3 className="feature-title">Session Management</h3>
          <p className="feature-description">
            Create, save, and manage multiple debug sessions. Easily track
            and organize your debugging workflow.
          </p>
        </div>

        <div className="feature-card">
          <div className="feature-icon">🔄</div>
          <h3 className="feature-title">Version Control</h3>
          <p className="feature-description">
            Track changes and maintain history of your debugging sessions.
            Roll back to previous states when needed.
          </p>
        </div>
      </div>
    </div>
  );
}
