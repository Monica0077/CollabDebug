import React, { useState, useRef, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import CollabEditor from './CollabEditor'; // MUST be wrapped with React.forwardRef
import { joinSession } from '../services/sessionApi';
import { runCode } from '../services/sandboxApi';
import { stopContainer as apiStopContainer, endSession as apiEndSession , leaveSession as apiLeaveSession} from '../services/SessionLifecycleApi';
import '../SessionRoom.css';
import ConfirmationModal from './ConfirmationModal';

// Use the modern @stomp/stompjs Client
import { Client } from '@stomp/stompjs'; 
import SockJS from 'sockjs-client';

// Define the correct backend WebSocket URL
const BACKEND_URL = 'http://localhost:8080/ws/session'; 


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

    // ✅ FIX 1: Ref to hold the Monaco Editor instance (requires CollabEditor to use forwardRef)
    const editorRef = useRef(null); 

    const [stompClient, setStompClient] = useState(null); 
    
    // ✅ Use a Ref to hold the latest currentUser for stable subscriptions
    const currentUserRef = useRef(currentUser);
    useEffect(() => {
        currentUserRef.current = currentUser;
    }, [currentUser]);

    const initialCode = {
        java: '// Welcome to CollabDebug!\npublic class Main {\n    public static void main(String[] args) {\n        System.out.println("Hello, World!");\n    }\n}',
        javascript: '// Welcome to CollabDebug!\nconsole.log("Hello, World!");',
        python: '# Welcome to CollabDebug!\nprint("Hello, World!")',
    };

    // Load session data on mount
    useEffect(() => {
        const loadSession = async () => {
            try{
                const session = await joinSession(sessionId);
                setOwner(session.ownerUsername || 'Unknown');
                setParticipants(session.participants || []);
                setCurrentUser(session.currentUser || 'Me');
                setLanguage(session.language || 'java');
                setCode(session.latestCode || initialCode[session.language || 'java'] || initialCode.java);
            } catch (err) {
                console.error("Failed to join session:", err);
            }
        };
        loadSession();
    }, [sessionId]);

    // --- WebSocket / STOMP Setup ---
    useEffect(() => {
        // 🔑 Get token
        const token = localStorage.getItem("token"); 
        if (!token) {
            console.error("JWT token is missing. WebSocket handshake will fail.");
            return; 
        }
        
        // 🔑 Construct the SockJS URL with the token in the query parameter
        const socketUrlWithToken = `${BACKEND_URL}?token=${token}`; 
        const authHeader = `Bearer ${token}`;
        
        // 🎯 Use the correct backend URL for SockJS
        const socket = new SockJS(socketUrlWithToken); 
        
        // 🎯 Use the modern Client API
        const client = new Client({ 
            webSocketFactory: () => socket, // Pass the SockJS instance as a factory
            reconnectDelay: 5000,
            debug: (str) => console.log(`[STOMP-Client] ${str}`),
            connectHeaders: { 
                Authorization: authHeader,
            },
        });
        
        // Set up connection handlers
        client.onConnect = () => {
            console.log('STOMP connected');

            // ✅ FIX 2: Subscribe to the public topic for successful edits (from other users)
            client.subscribe(`/topic/session/${sessionId}/edits`, (message) => {
                const edit = JSON.parse(message.body);
                
                // IMPORTANT: Only apply external edits (not the ones you sent)
                if(edit.userId !== currentUserRef.current) { 
                    const receivedCode = edit.op.text; // Assuming 'op' contains the full text

                    // Update local React state
                    setCode(receivedCode); 
                    
                    // 🚨 CRITICAL FIX 3: Update Monaco Editor directly using the ref
                    if (editorRef.current) {
                        // Use setValue to update the editor content
                        editorRef.current.setValue(receivedCode);
                        console.log(`[Collab] Editor content updated by ${edit.userId}.`);
                    }
                }
            });

            // ✅ FIX 4: Subscribe to the private queue for server resyncs/rejections
            client.subscribe(`/user/queue/edits`, (message) => {
                const response = JSON.parse(message.body);
                
                // Check if the response indicates the edit was NOT applied (a rejection)
                if (!response.applied) {
                    console.warn(`[Collab] Edit rejected. Server version: ${response.serverVersion}. Resyncing.`);
                    
                    // Update React State
                    setCode(response.updatedText);
                    
                    // Update Monaco Editor directly for resync
                    if (editorRef.current) {
                        editorRef.current.setValue(response.updatedText);
                        console.log("[Collab] Resynced editor to server state.");
                    }
                }
            });
            
            // TODO: Add subscriptions for terminal output and participant list updates here
        };
        
        client.activate();
        setStompClient(client);

        return () => {
            // Clean up on unmount
            if(client.connected) client.deactivate();
        };
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [sessionId]); 

    // Handler to send the local edit via WebSocket
    const sendEdit = useCallback((newCode) => {
        if(stompClient && stompClient.connected) { 
            
            // Defensive check: if newCode is empty or null, skip publishing.
            if (newCode === null || newCode === undefined) return; 

            const editMessage = {
                sessionId,
                userId: currentUserRef.current,
                // Assuming clientVersion tracking is not implemented yet, so it defaults to 0
                // clientVersion: currentVersion, 
                op: { 
                    type: 'replace',
                    text: newCode, 
                }
            };
            
            stompClient.publish({ 
                destination: `/app/session/${sessionId}/edit`, 
                body: JSON.stringify(editMessage) 
            });
        }
    }, [stompClient, sessionId]);

    const handleLanguageChange = (e) => {
        const newLang = e.target.value;
        setLanguage(newLang);
        const oldDefault = initialCode[language];
        // Only reset code if it hasn't been modified from the old language's default
        if(code === oldDefault) setCode(initialCode[newLang]);
    };

    const isOwner = currentUser === owner;

    // Memoized handler to update code locally AND send edit via WebSocket
    const handleCodeChange = useCallback((newCode) => {
        // Treat null/undefined from the editor as an empty string.
        const safeCode = newCode || ''; 
        
        setCode(safeCode); // Update local state first
        
        // Only proceed to send the edit if the value is a string
        if (typeof safeCode === 'string') {
            sendEdit(safeCode); 
        }
    }, [sendEdit]);


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
            // Pass the latest code to be saved before ending the session
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
                    <CollabEditor
                        // ✅ FIX 5: Pass the editorRef to the child component
                        ref={editorRef}
                        sessionId={sessionId}
                        currentUserId={currentUser}
                        code={code}
                        onCodeChange={handleCodeChange} 
                        language={language}
                    />
                </div>

                <div className="terminal-output"><pre>{terminalOutput}</pre></div>
            </div>

            <div className="session-side">
                <h3>Participants & Owner</h3>
                <div><strong>Owner:</strong> {owner}</div>
                <div className="participants-list">
                    {/* Assuming participants is an array of strings */}
                    {participants.map(p => (
                        <div key={p} className={`participant ${p === currentUser ? 'participant-you' : ''}`}>
                            {p} {p === currentUser && '(You)'}
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
