import React, { useState } from 'react';
import './SharedPages.css';

export default function Docs() {
  const [activeSection, setActiveSection] = useState('getting-started');

  return (
    <div className="page-container">
      <div className="docs-container">
        <aside className="docs-sidebar">
          <h3>Documentation</h3>
          <ul style={{ listStyle: 'none', padding: 0 }}>
            <li>
              <a
                href="#getting-started"
                onClick={() => setActiveSection('getting-started')}
                style={{ 
                  color: activeSection === 'getting-started' ? '#4299e1' : '#4a5568',
                  textDecoration: 'none'
                }}
              >
                Getting Started
              </a>
            </li>
            <li>
              <a
                href="#quickstart"
                onClick={() => setActiveSection('quickstart')}
                style={{ 
                  color: activeSection === 'quickstart' ? '#4299e1' : '#4a5568',
                  textDecoration: 'none'
                }}
              >
                Quickstart Guide
              </a>
            </li>
            <li>
              <a
                href="#api-reference"
                onClick={() => setActiveSection('api-reference')}
                style={{ 
                  color: activeSection === 'api-reference' ? '#4299e1' : '#4a5568',
                  textDecoration: 'none'
                }}
              >
                API Reference
              </a>
            </li>
          </ul>
        </aside>

        <main className="docs-content">
          {activeSection === 'getting-started' && (
            <div>
              <h2 className="section-title">Getting Started with CollabDebug</h2>
              <p>Welcome to CollabDebug! This guide will help you get started with our collaborative debugging platform.</p>
              
              <h3>Prerequisites</h3>
              <ul>
                <li>A modern web browser (Chrome, Firefox, Safari, or Edge)</li>
                <li>Valid account credentials</li>
              </ul>

              <h3>Installation</h3>
              <p>CollabDebug is a web-based platform, so there's no installation required. Simply:</p>
              <ol>
                <li>Navigate to the registration page</li>
                <li>Create your account</li>
                <li>Start debugging!</li>
              </ol>
            </div>
          )}

          {activeSection === 'quickstart' && (
            <div>
              <h2 className="section-title">Quickstart Guide</h2>
              <h3>Creating Your First Debug Session</h3>
              <p>Follow these steps to create your first collaborative debugging session:</p>
              
              <ol>
                <li>Log in to your account</li>
                <li>Click "New Session" in the dashboard</li>
                <li>Choose your programming language</li>
                <li>Invite collaborators (optional)</li>
                <li>Start coding!</li>
              </ol>

              <div className="code-block">
                <pre>{`// Example session creation
POST /api/sessions
{
  "language": "java",
  "title": "Debug Session 1",
  "description": "Debugging null pointer exception"
}`}</pre>
              </div>
            </div>
          )}

          {activeSection === 'api-reference' && (
            <div>
              <h2 className="section-title">API Reference</h2>
              <h3>Authentication</h3>
              <p>All API requests must be authenticated using JWT tokens.</p>
              
              <div className="code-block">
                <pre>{`// Authentication header
Authorization: Bearer your-jwt-token`}</pre>
              </div>

              <h3>Endpoints</h3>
              <ul>
                <li><code>/api/sessions</code> - Manage debug sessions</li>
                <li><code>/api/users</code> - User management</li>
                <li><code>/api/sandbox</code> - Sandbox operations</li>
              </ul>
            </div>
          )}
        </main>
      </div>
    </div>
  );
}
