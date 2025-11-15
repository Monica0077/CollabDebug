import React, { useState, useRef, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import CollabEditor from './CollabEditor'; // MUST be wrapped with React.forwardRef
import { joinSession } from '../services/sessionApi';
import { runCode } from '../services/sandboxApi';
import { stopContainer as apiStopContainer, endSession as apiEndSession , leaveSession as apiLeaveSession} from '../services/SessionLifecycleApi';
import '../SessionRoom.css';
import ConfirmationModal from './ConfirmationModal';
import SessionEndedModal from './SessionEndedModal';

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
    const [sessionActive, setSessionActive] = useState(true);
    const [showSessionEndModal, setShowSessionEndModal] = useState(false);
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
                // If the session was ended server-side, join will return 403.
                if (err?.response?.status === 403) {
                    const by = err.response?.data?.by || 'system';
                    const text = `Session is inactive (ended by ${by}).`;
                    setChatMessages(prev => [...prev, { userId: 'System', text: `${text} Click OK to return to dashboard.`, timestamp: Date.now() }]);
                    setTerminalOutput(prev => prev + `\n--- SESSION INACTIVE ---\nSession was ended by ${by}.`);
                    setSessionActive(false);
                    // Show modal and wait for user acknowledgement
                    setShowSessionEndModal(true);
                    return;
                }
            }
        };
        loadSession();
    }, [sessionId]);

    // Add periodic sync for participants list
    useEffect(() => {
        const syncParticipants = async () => {
            try {
                const session = await joinSession(sessionId);
                if (JSON.stringify(session.participants) !== JSON.stringify(participants)) {
                    console.log('[Presence] Syncing participants from server:', session.participants);
                    setParticipants(session.participants || []);
                }
            } catch (err) {
                console.error("Failed to sync participants:", err);
                // If session became inactive, stop polling and redirect users
                if (err?.response?.status === 403) {
                    const by = err.response?.data?.by || 'system';
                    const text = `Session ended by ${by}.`;
                    setChatMessages(prev => [...prev, { userId: 'System', text: `${text} Click OK to return to dashboard.`, timestamp: Date.now() }]);
                    setTerminalOutput(prev => prev + `\n--- SESSION INACTIVE ---\nSession was ended by ${by}.`);
                    setSessionActive(false);
                    setShowSessionEndModal(true);
                }
            }
        };

        // Sync every 30 seconds
        const syncInterval = setInterval(syncParticipants, 30000);
        return () => clearInterval(syncInterval);
    }, [sessionId, participants]);

    // ✅ NEW: Handle user leaving via navigate (back to dashboard) or closing the page
    useEffect(() => {
        const handleBeforeUnload = async (e) => {
            // This runs when: close tab, refresh, navigate away
            try {
                if (sessionActive && currentUserRef.current) {
                    console.log('[SessionRoom] User leaving page - disconnecting WebSocket and publishing leave event');
                    
                    // Disconnect STOMP client first
                    if (stompClientRef.current && stompClientRef.current.connected) {
                        console.log('[SessionRoom] Deactivating STOMP on page unload...');
                        stompClientRef.current.deactivate();
                    }
                    
                    // Call the leave API to notify backend (which publishes to Redis)
                    await apiLeaveSession(sessionId);
                }
            } catch (err) {
                console.error('[SessionRoom] Error during cleanup:', err);
            }
        };

        window.addEventListener('beforeunload', handleBeforeUnload);
        return () => {
            window.removeEventListener('beforeunload', handleBeforeUnload);
        };
    }, [sessionId, sessionActive]);

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
                // Use a Set to track message IDs we've already processed
                const messageId = message.headers['message-id'];
                if (messageId) {
                    setTerminalOutput(prev => {
                        const output = message.body;
                        if (prev.includes(output)) {
                            return prev; // Skip duplicate output
                        }
                        return prev + `\n--- RUN RESULT ---\n${output}`;
                    });
                }
            });
            
            // Chat Subscription
            client.subscribe(`/topic/session/${sessionId}/chat`, (message) => {
                const chatMessage = JSON.parse(message.body);
                const messageId = message.headers['message-id'];
                
                setChatMessages(prev => {
                    // Check if we already have this message (by content and timestamp)
                    const isDuplicate = prev.some(msg => 
                        msg.userId === chatMessage.userId && 
                        msg.text === chatMessage.text &&
                        msg.timestamp === chatMessage.timestamp
                    );
                    
                    if (isDuplicate) {
                        return prev;
                    }
                    return [...prev, chatMessage];
                });
            });
            
            //  Presence (Participants) Subscription
            client.subscribe(`/topic/session/${sessionId}/presence`, (message) => {
                try {
                    const event = JSON.parse(message.body);
                    // Enhanced logging
                    console.log(`[Presence] Received event:`, {
                        type: event.type,
                        userId: event.userId,
                        timestamp: new Date().toISOString()
                    });

                    if (event.type === 'joined') {
                        setParticipants(prev => {
                            const newList = prev.includes(event.userId) ? prev : [...prev, event.userId];
                            console.log('[Presence] After join - Current participants:', newList);
                            return newList;
                        });
                        // Add a small system chat message to notify users
                        setChatMessages(prev => [...prev, { userId: 'System', text: `${event.userId} joined the session`, timestamp: Date.now() }]);
                    } else if (event.type === 'left') {
                        setParticipants(prev => {
                            // Enhanced filtering with better logging
                            console.log(`[Presence] Attempting to remove user ${event.userId}`);
                            console.log(`[Presence] Current participants:`, prev);
                            
                            const newList = prev.filter(p => p !== event.userId);
                            
                            console.log(`[Presence] Updated participants:`, newList);
                            return newList;
                        });
                        // Add a small system chat message to notify users
                        setChatMessages(prev => [...prev, { userId: 'System', text: `${event.userId} left the session`, timestamp: Date.now() }]);
                    }
                } catch (error) {
                    console.error('[Presence] Error handling presence event:', error);
                }
            });

            // Meta (language / other session metadata) subscription
            client.subscribe(`/topic/session/${sessionId}/meta`, (message) => {
                try {
                    const meta = JSON.parse(message.body);
                    const userId = meta.userId;
                    const newLang = meta.language;

                    // Ignore events generated by ourselves
                    if (userId === currentUserRef.current) return;

                    if (newLang && newLang !== language) {
                        setLanguage(newLang);
                        // Set default code for the new language unless server provided code
                        const serverCode = meta.latestCode;
                        const newCode = serverCode || initialCode[newLang] || '';
                        setCode(newCode);
                        if (editorRef.current) editorRef.current.setValue(newCode);
                        setChatMessages(prev => [...prev, { userId: 'System', text: `${userId} changed language to ${newLang}`, timestamp: Date.now() }]);
                    }
                } catch (err) {
                    console.error('Error handling session meta event', err);
                }
            });

            // Session end subscription
            client.subscribe(`/topic/session/${sessionId}/end`, (message) => {
                try {
                    const event = JSON.parse(message.body);
                    const by = event.by || 'system';
                    const text = `Session ended by ${by}`;
                    setChatMessages(prev => [...prev, { userId: 'System', text, timestamp: Date.now() }]);
                    setTerminalOutput(prev => prev + `\n--- SESSION ENDED ---\n${text}`);
                    // Mark session inactive to prevent further actions
                    setSessionActive(false);
                    // Show full-screen modal; user will click OK to leave
                    setShowSessionEndModal(true);
                } catch (err) {
                    console.error('Error handling session end event', err);
                }
            });
        };
        
        client.onWebSocketClose = () => {
            console.warn('STOMP disconnected');
            setIsConnected(false); // Set connection state to false on close
            // When WebSocket closes, try to publish a final leave event if needed
            // This catches cases where the user closes the tab/window
            if (currentUserRef.current && sessionId) {
                console.log('[SessionRoom] WebSocket closed - attempting to publish leave event via API');
                apiLeaveSession(sessionId).catch(err => 
                    console.error('[SessionRoom] Could not publish leave on WebSocket close:', err)
                );
            }
        };
        client.activate();
        setStompClient(client);
        stompClientRef.current = client;

        return () => {
            // Clean up on unmount - this is crucial when navigating away
            console.log('[SessionRoom] Cleaning up STOMP client on unmount');
            if (client && client.connected) {
                console.log('[SessionRoom] Deactivating client on component unmount');
                client.deactivate();
            }
            setIsConnected(false);
            stompClientRef.current = null;
        };
    }, [sessionId]); 
    // --- Chat Handler ---
    const sendChatMessage = useCallback((e) => {
        e.preventDefault(); // Prevent form submission
        if(!sessionActive) {
            setChatMessages(prev => [...prev, { userId: 'System', text: 'Cannot send messages: session has ended', timestamp: Date.now() }]);
            return;
        }

        if(stompClient && stompClient.connected && chatInput.trim() !== '') {
            const chatMessage = {
                sessionId,
                userId: currentUserRef.current, // userId is mandatory in the DTO
                text: chatInput.trim(),
                timestamp: Date.now() // Add timestamp to help identify unique messages
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
        if(!sessionActive) return; // ignore edits after session end

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
        // Publish the language change to server so other clients receive it
        try {
            if (stompClient && stompClient.connected) {
                const payload = {
                    sessionId,
                    userId: currentUserRef.current,
                    language: newLang,
                    latestCode: code
                };
                stompClient.publish({ destination: `/app/session/${sessionId}/meta`, body: JSON.stringify(payload) });
            }
        } catch (err) {
            console.error('Failed to publish language change via STOMP', err);
        }
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
        if(!sessionActive) {
            setTerminalOutput(prev => prev + `\nERROR: Session has ended; cannot run code.`);
            return;
        }
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
        if(!sessionActive) {
            setTerminalOutput(prev => prev + `\nERROR: Session has ended; cannot stop container.`);
            return;
        }
        try {
            await apiStopContainer(sessionId);
            setTerminalOutput(prev => prev + `\n> Container stopped by ${currentUser}`);
        } catch (err) {
            setTerminalOutput(prev => prev + `\nERROR stopping container: ${err.message}`);
        }
    };

    const handleLeaveSession = async () => {
        try {
            console.log('[SessionRoom] User clicked "Leave Session" - cleaning up WebSocket and publishing leave event to backend');
            
            // Step 1: Disconnect STOMP client to trigger WebSocket close events
            if (stompClientRef.current && stompClientRef.current.connected) {
                console.log('[SessionRoom] Disconnecting STOMP client...');
                stompClientRef.current.deactivate();
                setIsConnected(false);
            }
            
            // Step 2: Wait a moment for WebSocket to close
            await new Promise(resolve => setTimeout(resolve, 200));
            
            // Step 3: Call the API to remove user from session DB and publish Redis presence event
            console.log('[SessionRoom] Calling leaveSession API...');
            await apiLeaveSession(sessionId);
            
            // Step 4: Wait a moment to allow Redis to process the presence event
            await new Promise(resolve => setTimeout(resolve, 300));
            
            // Step 5: Navigate back to dashboard
            console.log('[SessionRoom] Navigating to dashboard...');
            navigate('/dashboard');
        } catch (err) {
            console.error("Leave session failed", err);
            // Still navigate even if something fails
            navigate('/dashboard');
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
                    {!sessionActive && (
                        <div style={{color: 'crimson', marginTop: 8}}>Session has ended — you will be redirected shortly.</div>
                    )}
                    <div className="session-controls">
                       <select value={language} onChange={handleLanguageChange}>
                         <option value="java">Java</option>
                         <option value="javascript">JavaScript</option>
                         <option value="python">Python</option>
                       </select>

                        <button onClick={handleRunCode} disabled={!sessionActive}>▶ Run Code</button>
                        {isOwner && <button onClick={handleStopContainer} disabled={!sessionActive}>⏸ Stop Container</button>}
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
            {showSessionEndModal && (
                <SessionEndedModal
                    title="Session Ended"
                    message="This session has been ended. Click OK to return to the dashboard."
                    onAcknowledge={() => { setShowSessionEndModal(false); navigate('/dashboard'); }}
                />
            )}
        </div>
    );
};

export default SessionRoom;
