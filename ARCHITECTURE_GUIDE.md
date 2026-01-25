# CollabDebug Architecture - Complete Step-by-Step Guide

## Overview
CollabDebug is a **real-time collaborative debugging platform** that allows multiple users to:
- Share debugging sessions
- Collaborate on code in real-time
- Execute code in Docker containers
- Chat and communicate
- Manage participants dynamically

The architecture uses:
- **Frontend**: React with WebSocket (STOMP over SockJS)
- **Backend**: Spring Boot with WebSocket support
- **Message Broker**: Redis for pub/sub across instances
- **Database**: JPA for session persistence
- **Real-time**: Spring STOMP for WebSocket messaging

---

## Architecture Layers

```
┌─────────────────────────────────────────────────────────────┐
│                     FRONTEND (React)                         │
│  Components: Dashboard, SessionRoom, CollabEditor, Chat     │
│  Services: sessionApi, sandboxApi, SessionLifecycleApi      │
└──────────────┬──────────────────────────────────────────────┘
               │ (REST + WebSocket)
               ↓
┌─────────────────────────────────────────────────────────────┐
│                 BACKEND (Spring Boot)                        │
│  Controllers: SessionController, WebSocketHandlers          │
│  Services: SessionService, SandboxService                   │
│  Repositories: DebugSessionRepository                       │
└──────────────┬──────────────────────────────────────────────┘
               │ (STOMP Protocol)
               ↓
┌─────────────────────────────────────────────────────────────┐
│          MESSAGE BROKER & PERSISTENCE                       │
│  Redis (pub/sub) + Database (Sessions)                      │
│  Docker (Code Execution)                                    │
└─────────────────────────────────────────────────────────────┘
```

---

## Complete Flow: User Journey

### 1. User Logs In
**Frontend:**
- User authenticates via Login page
- JWT token stored in `localStorage`
- Token intercepted in all API requests via custom axios instance

**Backend:**
- JWT validated by `JwtAuthenticationFilter`
- User information extracted from token claims

---

### 2. User Views Dashboard
**Frontend (`Dashboard.jsx`):**
```
1. Component mounts → useEffect triggered
2. fetchSessions() called → listSessions() API
3. Display list of active sessions
4. User can:
   - Create a new session (button)
   - Join existing session (button)
   - Explore projects (tab)
```

**Backend (`SessionController.java`):**
```
GET /api/sessions
├─ SessionService.listActiveSessions()
├─ Returns: List<DebugSession> filtered by isActive=true
└─ Sorted by createdAt DESC
```

---

### 3. User Creates a New Session

#### Step 3A: CREATE SESSION

**Frontend (`CreateSessionModal.jsx`):**
```
User enters session name and clicks "Create"
│
├─ Call: createSession(sessionName)
│  Endpoint: POST /api/sessions/create
│  Payload: { name: "My Debug Session" }
│
├─ Response: { id: UUID, name, ownerUsername, participants: [] }
│
└─ Immediately join the created session
```

**Backend (`SessionController.java`):**
```
POST /api/sessions/create
├─ Extract username from Authentication principal
│
├─ SessionService.createSession(name, auth)
│  ├─ Create new DebugSession object
│  ├─ Set ownerUsername from auth
│  ├─ Set isActive = true
│  ├─ Initialize empty participants list
│  ├─ Initialize latestCode = ""
│  │
│  └─ Save to database (JPA)
│     └─ Returns: DebugSession with generated UUID
│
└─ Return to frontend
```

**Database State:**
```
DebugSession {
  id: UUID,
  name: "My Debug Session",
  ownerUsername: "john",
  isActive: true,
  participants: ["john"],
  latestCode: "",
  createdAt: timestamp,
  ...
}
```

---

#### Step 3B: JOIN SESSION (from create flow)

**Frontend (`CreateSessionModal.jsx`):**
```
After createSession succeeds:
│
├─ Call: joinSession(sessionId)
│  Endpoint: POST /api/sessions/join/{sessionId}
│
├─ Response: Updated DebugSession object
│
├─ Navigate to /session/{sessionId}
│
└─ Trigger SessionRoom component mount
```

**Backend (`SessionController.java`):**
```
POST /api/sessions/join/{sessionId}
├─ Extract username from Authentication
│
├─ SessionService.joinSession(sessionId, auth)
│  ├─ Find session by ID
│  ├─ Check if session.isActive (throw error if not)
│  ├─ Set currentUser = username
│  ├─ Add username to participants (if not already present)
│  │
│  ├─ Save to database
│  ├─ Force flush to ensure persistence
│  │
│  └─ Return updated session
│
└─ Response sent to frontend
```

**Database State After Join:**
```
DebugSession {
  ...
  participants: ["john"],  // Already in list from create
  currentUser: "john"
}
```

---

### 4. User Opens Session Room
**Frontend (`SessionRoom.jsx`):**

#### Initialization Phase:
```
Component Mounts
│
├─ useEffect 1: Load Session Data
│  ├─ joinSession(sessionId) API call (redundant but confirms)
│  ├─ Set: owner, sessionName, participants, currentUser
│  │
│  └─ Handle 403 error → session was ended server-side
│     └─ Show SessionEndedModal
│
├─ useEffect 2: Periodic Sync (every 30 seconds)
│  └─ Re-fetch participants from server
│     └─ Detect if session became inactive
│
└─ useEffect 3: WebSocket Setup (STOMP)
   └─ [See Section 5 below]
```

---

### 5. WebSocket Connection Established

**Frontend - WebSocket Setup Phase:**

```javascript
// STOMP Client initialization in SessionRoom.jsx

useEffect(() => {
  // Get JWT token from localStorage
  const token = localStorage.getItem("token");
  
  // Create SockJS connection with token in query param
  const socket = new SockJS(`http://localhost:8080/ws/session?token=${token}`);
  
  // Create STOMP client
  const client = new Client({
    webSocketFactory: () => socket,
    reconnectDelay: 5000,
    connectHeaders: { Authorization: `Bearer ${token}` },
  });
  
  // On connection successful
  client.onConnect = () => {
    console.log("STOMP Connected");
    
    // Subscribe to multiple topics
    client.subscribe(`/topic/session/${sessionId}/edits`, ...);
    client.subscribe(`/topic/session/${sessionId}/chat`, ...);
    client.subscribe(`/topic/session/${sessionId}/terminal`, ...);
    client.subscribe(`/topic/session/${sessionId}/presence`, ...);
    client.subscribe(`/topic/session/${sessionId}/meta`, ...);
    client.subscribe(`/topic/session/${sessionId}/end`, ...);
  };
  
  // Activate connection
  client.activate();
}, [sessionId]);
```

**Backend - WebSocket Handshake:**

```
1. HTTP Upgrade Request (SockJS)
   GET /ws/session?token=JWT_TOKEN
   │
   ├─ JwtHandshakeInterceptor intercepts
   │  ├─ Extract JWT from query param
   │  ├─ Validate JWT signature
   │  ├─ Extract username from claims
   │  ├─ Create Principal (username)
   │  └─ Store in session attributes
   │
   └─ HTTP 101 Upgrade → WebSocket

2. STOMP CONNECT
   │
   ├─ WebSocketConfig.configureClientInboundChannel()
   │  ├─ Extract principal from session attributes
   │  ├─ Set on message (accessor.setUser(principal))
   │  └─ Log authentication success
   │
   └─ Connection established in broker
```

**Key Point:** The JWT token is validated at the HTTP handshake, then the principal is attached to all STOMP messages.

---

### 6. User Joins Session (Presence Broadcast)

**Frontend - After WebSocket Connected:**
```
1. STOMP automatically triggers SessionSubscribeEvent when subscribing
2. Backend listens for subscription to presence topic
3. Backend calls sessionService.userJoined()
```

**Backend (`WebSocketSubscribeListener.java`):**
```
@EventListener
public void handleSessionSubscribe(SessionSubscribeEvent event) {
  // Extract destination: /topic/session/{sessionId}/presence
  // Extract connectionId: unique WebSocket connection ID
  // Extract userId: from principal
  
  if (destination contains "/presence") {
    sessionService.userJoined(sessionId, connectionId, userId);
  }
}
```

**Backend (`SessionService.java` - userJoined):**
```java
public void userJoined(String sessionId, String connectionId, String userId) {
  // Track connection-to-session mapping
  Set<String> connectionIds = sessionToLocalConnectionIds
    .computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet());
  
  boolean added = connectionIds.add(connectionId);
  
  if (added) {
    // First time this connection joined this session
    
    // Publish to Redis (for cross-instance delivery)
    redisPublisher.publishPresence(sessionId, 
      Map.of("type", "joined", "userId", userId)
    );
    
    // Also send directly to local WebSocket clients
    msgTemplate.convertAndSend(
      "/topic/session/" + sessionId + "/presence",
      Map.of("type", "joined", "userId", userId)
    );
  }
}
```

**Redis Pub/Sub Flow:**

```
1. RedisPublisher.publishPresence() sends to Redis channel
   Channel: "session-presence:{sessionId}"
   Payload: { "type": "joined", "userId": "jane" }

2. Redis routes to all subscribers (all backend instances)

3. RedisMessageSubscriber.onMessage() receives
   ├─ Parse JSON payload
   ├─ Extract sessionId from channel name
   │
   └─ Forward to local WebSocket clients
      msgTemplate.convertAndSend(
        "/topic/session/{sessionId}/presence",
        presence
      )
```

**Frontend - Receive Presence Event:**

```javascript
client.subscribe(`/topic/session/${sessionId}/presence`, (message) => {
  const event = JSON.parse(message.body);
  // event = { type: "joined", userId: "jane" }
  
  if (event.type === 'joined') {
    // Update participants list
    setParticipants(prev => {
      const newList = prev.includes(event.userId) 
        ? prev 
        : [...prev, event.userId];
      return newList;
    });
    
    // Add system chat message (with duplicate check)
    setChatMessages(prev => {
      const isDuplicate = prev.some(msg => 
        msg.userId === 'System' && 
        msg.text === `${event.userId} joined the session`
      );
      
      if (isDuplicate) return prev;
      
      return [...prev, { 
        userId: 'System', 
        text: `${event.userId} joined the session`, 
        timestamp: Date.now() 
      }];
    });
  }
});
```

**UI Update:**
```
Participants panel shows: ["john", "jane"]
Chat shows: "System: jane joined the session"
```

---

### 7. User Sends a Chat Message

**Frontend (`SessionRoom.jsx` - sendChatMessage):**
```javascript
const sendChatMessage = (e) => {
  e.preventDefault();
  
  if (stompClient && stompClient.connected && chatInput.trim() !== '') {
    const chatMessage = {
      sessionId,
      userId: currentUser,        // "john"
      text: chatInput.trim(),     // "How's the bug fix?"
      timestamp: Date.now()
    };
    
    // Publish to backend
    stompClient.publish({
      destination: `/app/session/${sessionId}/chat`,
      body: JSON.stringify(chatMessage)
    });
    
    setChatInput('');  // Clear input
  }
};
```

**Backend (`MessageController.java` or similar):**
```
STOMP Message arrives at /app/session/{sessionId}/chat

@MessageMapping("/session/{sessionId}/chat")
public void handleChatMessage(
  @Payload ChatMessage message,
  Principal principal,
  @DestinationVariable String sessionId
) {
  // Validate that sender is authenticated
  String username = principal.getName();
  
  // Publish to Redis for cross-instance delivery
  redisPublisher.publishChat(message);
  
  // Also send to local clients immediately
  msgTemplate.convertAndSend(
    "/topic/session/" + sessionId + "/chat",
    message
  );
}
```

**Redis Pub/Sub Flow:**
```
1. publishChat() → Redis channel: "session-chat:{sessionId}"
2. All subscribers receive message
3. RedisMessageSubscriber forwards to WebSocket topic
```

**Frontend - Receive Chat Message:**
```javascript
client.subscribe(`/topic/session/${sessionId}/chat`, (message) => {
  const chatMessage = JSON.parse(message.body);
  // chatMessage = { userId: "john", text: "How's the bug fix?", timestamp: ... }
  
  setChatMessages(prev => {
    // Check for duplicates (using timestamp and content)
    const isDuplicate = prev.some(msg => 
      msg.userId === chatMessage.userId && 
      msg.text === chatMessage.text &&
      msg.timestamp === chatMessage.timestamp
    );
    
    if (isDuplicate) return prev;
    return [...prev, chatMessage];
  });
});
```

---

### 8. User Edits Code in CollabEditor

**Frontend (`CollabEditor.jsx`):**
```javascript
// Monaco Editor change event
editor.onDidChangeModelContent(() => {
  const newCode = editor.getValue();
  onCodeChange(newCode);  // Callback from SessionRoom
});
```

**Frontend (`SessionRoom.jsx` - handleCodeChange):**
```javascript
const handleCodeChange = (newCode) => {
  // Update local state immediately (optimistic update)
  setCode(newCode);
  
  // Send to other users via WebSocket
  sendEdit(newCode);
};

const sendEdit = (newCode) => {
  if (stompClient && stompClient.connected && newCode !== null) {
    const editMessage = {
      sessionId,
      userId: currentUser,
      op: { 
        type: 'replace',
        text: newCode
      }
    };
    
    stompClient.publish({
      destination: `/app/session/${sessionId}/edit`,
      body: JSON.stringify(editMessage)
    });
  }
};
```

**Backend - Handle Edit:**
```
STOMP Message → /app/session/{sessionId}/edit

@MessageMapping("/session/{sessionId}/edit")
public void handleEdit(
  @Payload EditMessage edit,
  @DestinationVariable String sessionId,
  Principal principal
) {
  String userId = principal.getName();
  
  // Check if edit came from this user (skip own edits)
  if (edit.userId.equals(userId)) {
    // Skip - this is your own edit (already updated locally)
    return;
  }
  
  // Store new code as master version
  String newCode = edit.op.text;
  sessionService.documentMaster.put(sessionId, newCode);
  
  // Broadcast to other users
  redisPublisher.publishEdit(edit);
  msgTemplate.convertAndSend(
    "/topic/session/" + sessionId + "/edits",
    edit
  );
}
```

**Frontend - Receive External Edit:**
```javascript
client.subscribe(`/topic/session/${sessionId}/edits`, (message) => {
  const edit = JSON.parse(message.body);
  
  // Only apply if from another user
  if (edit.userId !== currentUser) {
    const receivedCode = edit.op.text;
    
    // Update React state
    setCode(receivedCode);
    
    // Update Monaco Editor directly via ref
    if (editorRef.current) {
      editorRef.current.setValue(receivedCode);
    }
  }
});
```

---

### 9. User Runs Code

**Frontend (`SessionRoom.jsx`):**
```javascript
const handleRunCode = async () => {
  // Set pending message
  setTerminalOutput(prev => 
    prev + `\n> Running code in ${language} sandbox...`
  );
  
  try {
    // Call API to execute
    await runCode(sessionId, language, code);
    // Response will come via WebSocket
  } catch (err) {
    setTerminalOutput(prev => prev + `\nERROR: ${err.message}`);
  }
};
```

**Frontend (`sandboxApi.jsx`):**
```javascript
export const runCode = async (sessionId, language, code) => {
  const response = await API.post(
    `/api/sandbox/run/${sessionId}`,
    { language, code }
  );
  return response.data;
};
```

**Backend (`SandboxController.java`):**
```
POST /api/sandbox/run/{sessionId}

├─ Extract language and code
├─ Create/get Docker container for session
├─ Execute code inside container
├─ Capture output
├─ Broadcast via WebSocket
└─ Response returned
```

**Real-time Output Broadcast:**
```
Backend publishes output to Redis:
  redisPublisher.publishTerminalOutput(sessionId, output);

Redis channel: "session-terminal:{sessionId}"

Frontend receives:
  client.subscribe(`/topic/session/${sessionId}/terminal`, ...)
  setTerminalOutput(prev => prev + output);
```

---

### 10. Second User Joins (Jane)

**Frontend (Jane's browser):**
```
Jane navigates to /session/{sessionId}
│
├─ SessionRoom component mounts
├─ joinSession(sessionId) called
├─ WebSocket connection established
├─ STOMP CONNECT → HTTP handshake with Jane's JWT
├─ STOMP SUBSCRIBE to presence topic
└─ WebSocketSubscribeListener.handleSessionSubscribe() triggered
```

**Backend:**
```
sessionService.userJoined("sessionId", "jane-connection-id", "jane")
│
├─ Add "jane-connection-id" to sessionToLocalConnectionIds
├─ publishPresence() → Redis
│  └─ Channel: "session-presence:sessionId"
│  └─ Payload: { "type": "joined", "userId": "jane" }
│
└─ msgTemplate.convertAndSend() → WebSocket topic
   └─ Topic: "/topic/session/sessionId/presence"
```

**Redis Broadcasts to All Instances:**
```
RedisMessageSubscriber receives on "session-presence:sessionId"
│
└─ Forward to local WebSocket clients
   msgTemplate.convertAndSend("/topic/session/sessionId/presence", ...)
```

**Frontend (Both John and Jane):**
```
Both receive presence event:
{ type: "joined", userId: "jane" }

John's screen:
  Participants: ["john", "jane"]
  Chat: "System: jane joined the session"

Jane's screen:
  Participants: ["john", "jane"]  (from joinSession API response)
  Chat updates with presence message
```

---

### 11. User Leaves Session

**Frontend (`SessionRoom.jsx` - handleLeaveSession):**
```javascript
const handleLeaveSession = async () => {
  try {
    // Step 1: Disconnect WebSocket
    if (stompClientRef.current && stompClientRef.current.connected) {
      stompClientRef.current.deactivate();
      setIsConnected(false);
    }
    
    // Step 2: Wait for WebSocket to close
    await new Promise(resolve => setTimeout(resolve, 200));
    
    // Step 3: Call leave API
    await apiLeaveSession(sessionId);  // POST /api/sessions/leave/{sessionId}
    
    // Step 4: Wait for Redis processing
    await new Promise(resolve => setTimeout(resolve, 300));
    
    // Step 5: Navigate away
    navigate('/dashboard');
  } catch (err) {
    console.error("Leave failed", err);
    navigate('/dashboard');
  }
};
```

**Backend (`SessionController.java`):**
```
POST /api/sessions/leave/{sessionId}

├─ Extract username from Authentication
│
├─ SessionService.leaveSession(sessionId, auth)
│  ├─ Find session in database
│  ├─ Remove username from participants list
│  ├─ Save to database
│  ├─ Force flush to DB
│  │
│  ├─ Publish presence "left" event to Redis
│  │  redisPublisher.publishPresence(sessionId, 
│  │    Map.of("type", "left", "userId", username)
│  │  )
│  │
│  ├─ Also send to local WebSocket clients immediately
│  │  msgTemplate.convertAndSend(
│  │    "/topic/session/" + sessionId + "/presence",
│  │    {type: "left", userId: username}
│  │  )
│  │
│  └─ If no participants remain → stop Docker container
│
└─ Return 200 OK
```

**Redis Pub/Sub for Leave:**
```
1. publishPresence() → "session-presence:sessionId"
2. Channel: { "type": "left", "userId": "jane" }
3. All subscribers forward to WebSocket
```

**Frontend (John):**
```
Receive presence event: { type: "left", userId: "jane" }

Update participants:
  setParticipants(prev => prev.filter(p => p !== "jane"))
  
Show chat message:
  "System: jane left the session"

Participants now: ["john"]
```

---

### 12. Owner Ends Session

**Frontend (`SessionRoom.jsx` - handleEndSessionConfirmed):**
```javascript
const handleEndSessionConfirmed = async () => {
  try {
    // Save latest code and end session
    await apiEndSession(sessionId, { latestCode: code });
    
    setShowEndConfirm(false);
    navigate('/dashboard');
  } catch (err) {
    console.error("End session failed", err);
  }
};
```

**Backend (`SessionController.java`):**
```
POST /api/sessions/end/{sessionId}

├─ Validate user is session owner
│
├─ SessionService.endSession(sessionId, auth, saveCode)
│  ├─ Find session
│  ├─ Update latestCode if provided
│  ├─ Set isActive = false
│  ├─ Save to database
│  │
│  ├─ Publish session-end event to Redis
│  │  redisPublisher.publishSessionEnded(sessionId,
│  │    Map.of("by", username)
│  │  )
│  │
│  ├─ Send to WebSocket clients
│  │  msgTemplate.convertAndSend(
│  │    "/topic/session/" + sessionId + "/end",
│  │    {by: username}
│  │  )
│  │
│  ├─ Stop Docker container
│  │  stopContainer(sessionId)
│  │
│  └─ Clean up internal data structures
│     - Remove from documentMaster
│     - Remove from serverVersion
│     - Remove connection mappings
│
└─ Return 200 OK
```

**Frontend (All Connected Clients):**
```
Receive end event from WebSocket:
/topic/session/{sessionId}/end

event = { by: "john" }

Actions:
  1. setChatMessages: Add "System: Session ended by john"
  2. setTerminalOutput: Add "--- SESSION ENDED ---"
  3. setSessionActive(false)
  4. Show SessionEndedModal
  
User clicks OK → navigate('/dashboard')
```

---

### 13. User Returns to Dashboard

**Frontend (`Dashboard.jsx`):**
```
useEffect: Fetch active sessions

// Backend will NOT include the ended session
// because listActiveSessions() filters by isActive=true

setParticipants(fetchedSessions)
```

**UI Reflects:**
```
- Old session is no longer in the list
- Other active sessions are displayed
- User can create/join other sessions
```

---

## Key Components & Files

### Frontend Structure

| File | Purpose |
|------|---------|
| `Dashboard.jsx` | Main hub, list active sessions |
| `SessionRoom.jsx` | Collab session container, WebSocket setup |
| `CollabEditor.jsx` | Monaco editor with Monaco binding |
| `ChatPanel.jsx` | Chat interface (integrated in SessionRoom) |
| `sessionApi.jsx` | REST APIs for sessions |
| `sandboxApi.jsx` | Code execution APIs |
| `SessionLifecycleApi.jsx` | Leave, end session APIs |

### Backend Structure

| File | Purpose |
|------|---------|
| `SessionController.java` | REST endpoints |
| `SessionService.java` | Session business logic |
| `WebSocketConfig.java` | STOMP endpoint configuration |
| `JwtHandshakeInterceptor.java` | JWT validation at handshake |
| `WebSocketSubscribeListener.java` | Listen for STOMP subscribe events |
| `RedisPublisher.java` | Publish to Redis channels |
| `RedisMessageSubscriber.java` | Listen to Redis and forward to WebSocket |
| `DebugSessionRepository.java` | JPA for database queries |

---

## Communication Flow Summary

### REST Communication (Frontend ↔ Backend)
```
Request: POST /api/sessions/create
Response: DebugSession object
Authentication: JWT in Authorization header
```

### WebSocket Communication (Bidirectional)

**Client to Server (Publisher):**
```
STOMP SEND → /app/session/{sessionId}/chat
            /app/session/{sessionId}/edit
            /app/session/{sessionId}/meta
```

**Server to Client (Subscriber):**
```
STOMP SUBSCRIBE ← /topic/session/{sessionId}/chat
                  /topic/session/{sessionId}/edits
                  /topic/session/{sessionId}/terminal
                  /topic/session/{sessionId}/presence
                  /topic/session/{sessionId}/end
                  /topic/session/{sessionId}/meta
```

### Redis Pub/Sub (Cross-Instance)
```
Channels:
  session-updates:{sessionId}
  session-chat:{sessionId}
  session-presence:{sessionId}
  session-terminal:{sessionId}
  session-end:{sessionId}
  session-meta:{sessionId}
```

---

## Duplicate Prevention

To prevent duplicate messages (as you experienced), the frontend checks:

**For Presence (Join/Leave):**
```javascript
const isDuplicate = prev.some(msg => 
  msg.userId === 'System' && 
  msg.text === `${event.userId} joined the session`
);
```

**For Chat Messages:**
```javascript
const isDuplicate = prev.some(msg => 
  msg.userId === chatMessage.userId && 
  msg.text === chatMessage.text &&
  msg.timestamp === chatMessage.timestamp
);
```

This is because messages can arrive via:
1. Direct WebSocket (immediate)
2. Redis (if from another instance)

The check prevents displaying the same message twice.

---

## Error Handling

### Session Not Found
```
Backend: 404 Not Found
Frontend: Show error toast, redirect to dashboard
```

### Session Already Ended
```
Backend: 403 Forbidden (in joinSession)
Frontend: Show SessionEndedModal, disallow actions
```

### WebSocket Disconnected
```
Frontend: onWebSocketClose() triggered
Actions:
  - Attempt to publish leave via REST API
  - Mark connection as closed
  - Disable chat/edit functionality
```

### Code Execution Failed
```
Backend: Error returned from Docker
Frontend: Display in terminal output
User can retry or debug further
```

---

## Data Persistence

### In Database (PostgreSQL/MySQL)
- Session metadata (name, owner, participants)
- Latest code snapshot
- Creation timestamp
- Active status

### In Memory (Backend JVM)
- Document master (current code)
- Server version counter
- Connection-to-user mappings
- Session-to-connection mappings

### In Redis
- Temporary pub/sub channels
- Optional: chat history, for durability

### In Docker
- Running code execution sandboxes
- Container state

---

## Summary

CollabDebug achieves real-time collaboration through:

1. **JWT Authentication** for secure WebSocket connections
2. **STOMP Protocol** for bidirectional messaging
3. **Redis Pub/Sub** for cross-instance communication
4. **Database Transactions** for state consistency
5. **Docker Containers** for isolated code execution
6. **Duplicate Detection** to prevent duplicate messages
7. **Event-driven Architecture** with listeners for various events

The system handles complex scenarios like:
- Multiple users joining/leaving
- Concurrent code edits
- Cross-instance communication
- Graceful session cleanup
- Real-time presence updates

