import React, { useState, useRef, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import Editor from '@monaco-editor/react';
import { joinSession } from '../services/sessionApi';
import '../SessionRoom.css'; // <-- Import the CSS

const languageOptions = [
  { value: 'java', label: 'Java' },
  { value: 'javascript', label: 'JavaScript' },
  { value: 'python', label: 'Python' },
];

const initialCode = {
  java: '// Welcome to CollabDebug!\npublic class Main {\n    public static void main(String[] args) {\n        System.out.println("Hello, World!");\n    }\n}',
  javascript: '// Welcome to CollabDebug!\nconsole.log("Hello, World!");',
  python: '# Welcome to CollabDebug!\nprint("Hello, World!")',
};

const SessionRoom = () => {
  const { sessionId } = useParams();
  const [code, setCode] = useState(initialCode.java);
  const [language, setLanguage] = useState('java');
  const editorRef = useRef(null);
  const [terminalOutput, setTerminalOutput] = useState("--- Terminal Output ---\nReady to run code...");

  const [owner, setOwner] = useState('');
  const [participants, setParticipants] = useState([]);
  const [currentUser, setCurrentUser] = useState('');

  useEffect(() => {
    const loadSession = async () => {
      try {
        const session = await joinSession(sessionId);
        setOwner(session.ownerUsername || 'Unknown');
        setParticipants(session.participants || []);
        setCurrentUser(session.currentUser || 'Me');
        setCode(initialCode[language] || initialCode.java);
      } catch (err) {
        console.error("Failed to join session:", err);
      }
    };
    loadSession();
  }, [sessionId, language]);

  const handleEditorDidMount = (editor) => { editorRef.current = editor; };
  const handleEditorChange = (value) => { setCode(value); };
  const handleRunCode = async () => {
    setTerminalOutput(prev => prev + `\n> Running code in ${language} sandbox...`);
  };
  const handleLanguageChange = (e) => {
    const newLang = e.target.value;
    setLanguage(newLang);
    if(code.trim() === initialCode[language].trim()) setCode(initialCode[newLang]);
  };

  return (
    <div className="session-room-container">
      <div className="session-main">
        <div className="session-header">
          <h2>Session: {sessionId.substring(0,8)}...</h2>
          <div className="session-controls">
            <select value={language} onChange={handleLanguageChange}>
              {languageOptions.map(opt => <option key={opt.value} value={opt.value}>{opt.label}</option>)}
            </select>
            <button onClick={handleRunCode}>▶ Run Code</button>
          </div>
        </div>

        <div className="editor-container">
          <Editor
            height="100%"
            language={language}
            theme="vs-dark"
            value={code}
            onChange={handleEditorChange}
            onMount={handleEditorDidMount}
            options={{ minimap: { enabled: false }, fontSize: 16 }}
          />
        </div>

        <div className="terminal-output">
          <pre>{terminalOutput}</pre>
        </div>
      </div>

      <div className="session-side">
        <h3>Participants & Owner</h3>
        <div className="session-owner"><strong>Owner:</strong> {owner}</div>
        <div className="participants-list">
          {participants.map(p => (
            <div key={p.username} className={`participant ${p.username === currentUser ? 'participant-you' : ''}`}>
              {p.username} {p.username === currentUser && '(You)'}
            </div>
          ))}
          {participants.length === 0 && <p className="no-participants">No participants yet.</p>}
        </div>
      </div>
    </div>
  );
};

export default SessionRoom;
