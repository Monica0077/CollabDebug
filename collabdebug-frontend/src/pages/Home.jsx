import React from 'react';
import { Link } from 'react-router-dom';
import './SharedPages.css';

export default function Home() {
  return (
    <div className="page-container">
      <section className="hero-section">
        <h1 className="hero-title">Welcome to CollabDebug</h1>
        <p className="hero-subtitle">
          Transform your debugging experience with real-time collaboration. 
          Debug smarter, solve faster, learn together.
        </p>
        <Link to="/dashboard" className="cta-button">Get Started Now</Link>
      </section>

      <div className="feature-grid">
        <div className="feature-card">
          <div className="feature-icon">🔒</div>
          <h3 className="feature-title">Secure Sandboxing</h3>
          <p className="feature-description">
            Run and test code in isolated environments with enterprise-grade security.
          </p>
        </div>

        <div className="feature-card">
          <div className="feature-icon">👥</div>
          <h3 className="feature-title">Real-time Collaboration</h3>
          <p className="feature-description">
            Debug together with your team in real-time, share insights instantly.
          </p>
        </div>

        <div className="feature-card">
          <div className="feature-icon">⚡</div>
          <h3 className="feature-title">Instant Setup</h3>
          <p className="feature-description">
            No complex configuration needed. Start debugging in seconds.
          </p>
        </div>
      </div>

      <section style={{ textAlign: 'center', margin: '4rem 0' }}>
        <h2 className="section-title">Ready to revolutionize your debugging workflow?</h2>
        <Link to="/features" className="cta-button">Explore Features</Link>
      </section>
    </div>
  );
}
