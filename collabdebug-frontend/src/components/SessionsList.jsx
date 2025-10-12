// src/components/SessionList.jsx

import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { listSessions, createSession, joinSession } from '../services/sessionApi';

const SessionList = () => {
  const [sessions, setSessions] = useState([]);
  const [newSessionName, setNewSessionName] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const navigate = useNavigate();

  const fetchSessions = async () => {
    try {
      setLoading(true);
      const data = await listSessions();
      setSessions(data);
      setError(null);
    } catch (err) {
      setError('Failed to fetch sessions. Are you logged in?');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSessions();
  }, []);

  const handleCreateSession = async (e) => {
    e.preventDefault();
    if (!newSessionName.trim()) return;

    try {
      const newSession = await createSession(newSessionName);
      // After creation, immediately join and navigate to the session room
      handleJoinSession(newSession.id);
    } catch (err) {
      setError('Could not create session.');
      console.error(err);
    }
  };

  const handleJoinSession = async (sessionId) => {
    try {
      // The join API call confirms membership
      await joinSession(sessionId);
      // Navigate to the main collaboration room
      navigate(`/session/${sessionId}`); 
    } catch (err) {
      setError(`Could not join session ${sessionId}.`);
      console.error(err);
    }
  };

  if (loading) return <div>Loading Sessions...</div>;
  if (error) return <div style={{ color: 'red' }}>Error: {error}</div>;

  return (
    <div className="p-8 max-w-4xl mx-auto">
      <h1 className="text-3xl font-bold mb-6">Active Debug Sessions</h1>
      
      {/* Create Session Form */}
      <form onSubmit={handleCreateSession} className="mb-8 p-4 border rounded-lg shadow-sm bg-gray-50">
        <h2 className="text-xl font-semibold mb-3">Start a New Session</h2>
        <input
          type="text"
          value={newSessionName}
          onChange={(e) => setNewSessionName(e.target.value)}
          placeholder="Enter a session name (e.g., 'Fix Login Bug')"
          className="p-2 border rounded w-80 mr-4"
          required
        />
        <button
          type="submit"
          className="bg-blue-500 hover:bg-blue-600 text-white font-bold py-2 px-4 rounded"
        >
          Create & Join
        </button>
      </form>

      {/* Sessions List */}
      <div className="space-y-4">
        {sessions.length === 0 ? (
          <p>No active sessions. Be the first to create one!</p>
        ) : (
          sessions.map((session) => (
            <div key={session.id} className="flex justify-between items-center p-4 border rounded-lg shadow">
              <div>
                <p className="text-lg font-medium">{session.name}</p>
                <p className="text-sm text-gray-600">Owner: **{session.ownerUsername}** | Started: {new Date(session.createdAt).toLocaleDateString()}</p>
              </div>
              <button
                onClick={() => handleJoinSession(session.id)}
                className="bg-green-500 hover:bg-green-600 text-white font-bold py-2 px-4 rounded"
              >
                Join Session
              </button>
            </div>
          ))
        )}
      </div>
    </div>
  );
};

export default SessionList;