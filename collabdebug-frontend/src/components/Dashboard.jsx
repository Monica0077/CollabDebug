import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import '../Dashboard.css';
import { listSessions, joinSession } from '../services/sessionApi';
import CreateSessionModal from './CreateSessionModal';
import { FaPlus, FaCode, FaUsers, FaSpinner } from 'react-icons/fa';

const Dashboard = () => {
  const [sessions, setSessions] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const navigate = useNavigate();

  const fetchSessions = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await listSessions();
      setSessions(data);
    } catch (err) {
      console.error("Failed to fetch sessions:", err);
      setError("Failed to load sessions. Please check your login status.");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchSessions();
  }, []);

  const handleJoinSession = async (sessionId) => {
    try {
      await joinSession(sessionId);
      navigate(`/session/${sessionId}`);
    } catch (err) {
      console.error("Failed to join session:", err);
      alert(`Could not join session: ${err.response?.data || err.message}`);
    }
  };

  const handleSessionCreated = (sessionName) => {
      console.log(`Session "${sessionName}" created and joined successfully!`);
  };

  if (isLoading) {
    return (
      <div className="dashboard-loading">
        <FaSpinner />
        <span>Loading active sessions...</span>
      </div>
    );
  }

  return (
    <div className="dashboard-container">
      <h1>Collaboration Dashboard</h1>

      {/* Action Cards */}
      <div className="dashboard-cards">
        {/* Create Session Card */}
        <div 
          className="card-create-session"
          onClick={() => setIsModalOpen(true)}
        >
          <FaPlus />
          <h2>Create New Session</h2>
          <p>Start a new shared coding room.</p>
        </div>

        {/* Explore Projects */}
        <div className="card-default">
          <FaCode />
          <h2>Explore Projects</h2>
          <p>View your existing codebases.</p>
        </div>

        {/* My Team */}
        <div className="card-default">
          <FaUsers />
          <h2>My Team</h2>
          <p>Manage participants and access.</p>
        </div>
      </div>

      {/* Active Sessions List */}
      <h2>Active Collaboration Rooms ({sessions.length})</h2>

      {error && <div className="dashboard-error">{error}</div>}

      {sessions.length === 0 && !isLoading && !error ? (
        <div className="dashboard-empty">
          <p>No active sessions found. Be the first to create one!</p>
        </div>
      ) : (
        <div className="sessions-list">
          {sessions.map((session) => (
            <div key={session.id} className="session-card">
              <div>
                <h3>{session.name}</h3>
                <p>ID: {session.id.substring(0, 8)}... | Owner: {session.ownerUsername || 'N/A'}</p>
              </div>
              <button onClick={() => handleJoinSession(session.id)}>Join Session</button>
            </div>
          ))}
        </div>
      )}

      {/* The Modal */}
      {isModalOpen && (
        <CreateSessionModal 
          onClose={() => setIsModalOpen(false)}
          onSuccess={handleSessionCreated}
        />
      )}
    </div>
  );
};

export default Dashboard;
