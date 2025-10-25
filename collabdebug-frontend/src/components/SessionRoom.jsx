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
    const [chatMessages, setChatMessages] = useState([]);
    const [chatInput, setChatInput] = useState('');
    // ✅ FIX 1: Ref to hold the Monaco Editor instance (requires CollabEditor to use forwardRef)
    const editorRef = useRef(null); 
    const chatEndRef = useRef(null);
    const stompClientRef = useRef(null);
    
    const [stompClient, setStompClient] = useState(null); 
    const [isConnected, setIsConnected] = useState(false);
    
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
        if (stompClientRef.current) {
            console.log('STOMP client already active. Skipping setup.');
            return; 
        }
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
            setIsConnected(true);

            // Subscribe to the public topic for successful edits (from other users)
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

            // Subscribe to the private queue for server resyncs/rejections
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
            // Terminal Output Subscription
            client.subscribe(`/topic/session/${sessionId}/terminal`, (message) => {
                // message.body should be the raw output string from the Redis subscriber
                setTerminalOutput(prev => prev + `\n--- RUN RESULT ---\n${message.body}`);
                console.log("[Terminal] Received new output.");
            });
            
            // Chat Subscription
            client.subscribe(`/topic/session/${sessionId}/chat`, (message) => {
                const chatMessage = JSON.parse(message.body);
                setChatMessages(prev => [...prev, chatMessage]);
            });
            
            //  Presence (Participants) Subscription
            client.subscribe(`/topic/session/${sessionId}/presence`, (message) => {
                const event = JSON.parse(message.body);
                if (event.type === 'joined') {
                    setParticipants(prev => {
                        if (!prev.includes(event.userId)) {
                            return [...prev, event.userId];
                        }
                        return prev;
                    });
                } else if (event.type === 'left') {
                    setParticipants(prev => prev.filter(p => p !== event.userId));
                }
            });
        };
        
        client.onWebSocketClose = () => {
        console.warn('STOMP disconnected');
        setIsConnected(false); // Set connection state to false on close
        };
        client.activate();
        setStompClient(client);
        stompClientRef.current = client;

        return () => {
            // Clean up on unmount
            if(client.connected) client.deactivate();
            setIsConnected(false);
            stompClientRef.current = null;
        };
    }, [sessionId]); 
    // --- Chat Handler ---
    const sendChatMessage = useCallback((e) => {
        e.preventDefault(); // Prevent form submission
        if(stompClient && stompClient.connected && chatInput.trim() !== '') {
            const chatMessage = {
                sessionId,
                userId: currentUserRef.current, // userId is mandatory in the DTO
                text: chatInput.trim(),
            };
            
            stompClient.publish({
                destination: `/app/session/${sessionId}/chat`,
                body: JSON.stringify(chatMessage)
            });
            
            setChatInput(''); // Clear input after sending
        }
    }, [stompClient, sessionId, chatInput]);
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
        // Only update with a starting message locally
        setTerminalOutput(prev => prev + `\n> Running code in ${language} sandbox (waiting for result via WebSocket)...`);
        
        try {
            // The runCode API call triggers the backend to execute and then publish to Redis/WebSocket
            await runCode(sessionId, language, code);
            // DO NOT setTerminalOutput with the return value here, 
            // the synchronization logic handles the update via the subscription.
            
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
                {/* ... (Session Header remains the same) ... */}
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
                
                {/* Editor and Terminal */}
                <div className="editor-terminal-area">
                    <div className="editor-container">
                        <CollabEditor
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
            </div>

            {/* Sidebar with Participants and Chat */}
            <div className="session-side">
                {/* Participants List (Upper half of sidebar) */}
                <div className="side-panel participants-panel">
                    <h3>Participants</h3>
                    <div><strong>Owner:</strong> {owner}</div>
                    <div className="participants-list">
                        {participants.map(p => (
                            <div key={p} className={`participant ${p === currentUser ? 'participant-you' : ''}`}>
                                {p} {p === currentUser && '(You)'}
                            </div>
                        ))}
                    </div>
                </div>

                {/* Chat Panel (Lower half of sidebar) */}
                <div className="side-panel chat-panel">
                    <h3>Session Chat</h3>
                    <div className="chat-messages">
                        {chatMessages.map((msg, index) => (
                            <div key={index} className={`chat-message ${msg.userId === currentUser ? 'message-self' : 'message-other'}`}>
                                <strong>{msg.userId === currentUser ? 'You' : msg.userId}:</strong> {msg.text}
                            </div>
                        ))}
                        <div ref={chatEndRef} /> {/* Scroll target */}
                    </div>
                    <form onSubmit={sendChatMessage} className="chat-input-form">
                        <input
                            type="text"
                            value={chatInput}
                            onChange={(e) => setChatInput(e.target.value)}
                            placeholder="Type a message..."
                            disabled={!isConnected}
                        />
                        <button type="submit" disabled={!isConnected || chatInput.trim() === ''}>Send</button>
                    </form>
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
