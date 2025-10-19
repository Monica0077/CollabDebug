// src/components/SessionRoom.jsx
import React, { useState, useRef, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import Editor from '@monaco-editor/react';
import { joinSession } from '../services/sessionApi';
import { runCode } from '../services/sandboxApi';
import { stopContainer as apiStopContainer, endSession as apiEndSession , leaveSession as apiLeaveSession} from '../services/SessionLifecycleApi';
import '../SessionRoom.css';
import ConfirmationModal from './ConfirmationModal'; // create a simple reusable modal

const SessionRoom = () => {
  const { sessionId } = useParams();
  const navigate = useNavigate();
  const [code, setCode] = useState('');
  const [language, setLanguage] = useState('java');
  const [terminalOutput, setTerminalOutput] = useState("--- Terminal Output ---\nReady to run code...");
  const [owner, setOwner] = useState('');
  const [participants, setParticipants] = useState([]);
  const [currentUser, setCurrentUser] = useState('');
  const [showEndConfirm, setShowEndConfirm] = useState(false);
  const initialCode = {
  java: '// Welcome to CollabDebug!\npublic class Main {\n    public static void main(String[] args) {\n        System.out.println("Hello, World!");\n    }\n}',
  javascript: '// Welcome to CollabDebug!\nconsole.log("Hello, World!");',
  python: '# Welcome to CollabDebug!\nprint("Hello, World!")',
  };
  // load session on mount
  useEffect(() => {
    const loadSession = async () => {
      try{
      const session = await joinSession(sessionId);
      setOwner(session.ownerUsername || 'Unknown');
      setParticipants(session.participants || []);
      setCurrentUser(session.currentUser || 'Me');
      setLanguage(session.language || 'java');
      setCode(session.latestCode || initialCode[session.language || 'java'] || initialCode.java);
    }catch (err) {
      console.error("Failed to join session:", err);
    }
  };
    loadSession();
  }, [sessionId]);

  const handleLanguageChange = (e) => {
  const newLang = e.target.value;
  setLanguage(newLang);

  // If the current code is still the default code for the old language, switch it
  const oldDefault = initialCode[language];
  if (code === oldDefault) {
    setCode(initialCode[newLang]);
  }
};
  const isOwner = currentUser === owner;
  console.log(`SessionRoom mounted for sessionId: ${sessionId}, currentUser: ${currentUser}, owner: ${owner}`);

  const handleRunCode = async () => {
    setTerminalOutput(prev => prev + `\n> Running code in ${language} sandbox...`);
    try {
      const output = await runCode(sessionId, language, code);
      setTerminalOutput(prev => prev + `\n${output}`);
    } catch (err) {
      setTerminalOutput(prev => prev + `\nERROR: ${err.response?.data || err.message}`);
    }
  };

  const handleStopContainer = async () => {
    try {
      await apiStopContainer(sessionId);
      setTerminalOutput(prev => prev + `\n> Container stopped by ${currentUser}`);
    } catch (err) {
      setTerminalOutput(prev => prev + `\nERROR stopping container: ${err.message}`);
    }
  };

  const handleLeaveSession = async () => {
    try {
      await apiLeaveSession(sessionId);
      navigate('/dashboard');
    } catch (err) {
      console.error("Leave session failed", err);
    }
  };

  const handleEndSessionConfirmed = async () => {
    try {
      // call end API with the latest code to save
      await apiEndSession(sessionId, { latestCode: code });
      setShowEndConfirm(false);
      navigate('/dashboard');
    } catch (err) {
      console.error("End session failed", err);
    }
  };

  return (
    <div className="session-room-container">
      <div className="session-main">
        <div className="session-header">
          <h2>Session: {sessionId.substring(0,8)}...</h2>
          <div className="session-controls">
           <select value={language} onChange={handleLanguageChange}>
            <option value="java">Java</option>
            <option value="javascript">JavaScript</option>
            <option value="python">Python</option>
          </select>

            <button onClick={handleRunCode}>▶ Run Code</button>

            {isOwner && <button onClick={handleStopContainer}>⏸ Stop Container</button>}

            <button onClick={handleLeaveSession}>↩ Leave Session</button>

            {isOwner && (
              <button className="danger" onClick={() => setShowEndConfirm(true)}>
                ✖ End Session
              </button>
            )}
          </div>
        </div>

        <div className="editor-container">
          <Editor height="100%" language={language} theme="vs-dark" value={code}
            onChange={v => setCode(v)} />
        </div>

        <div className="terminal-output"><pre>{terminalOutput}</pre></div>
      </div>

      <div className="session-side">
        <h3>Participants & Owner</h3>
        <div><strong>Owner:</strong> {owner}</div>
        <div className="participants-list">
          {participants.map(p => (
            <div key={p.username} className={`participant ${p.username === currentUser ? 'participant-you' : ''}`}>
              {p.username} {p.username === currentUser && '(You)'}
            </div>
          ))}
        </div>
      </div>

      {showEndConfirm && (
        <ConfirmationModal
          title="End Session"
          message="Ending the session will stop and remove the session's container and mark the session inactive. The latest code will be saved. Are you sure?"
          onConfirm={handleEndSessionConfirmed}
          onCancel={() => setShowEndConfirm(false)}
        />
      )}
    </div>
  );
};

export default SessionRoom;
