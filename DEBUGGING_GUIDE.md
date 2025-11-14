# Debugging Guide - Real-Time Diagnostics

## Current Status

The system is **partially working** but presence events are not being delivered in real-time. The issue is likely in one of these areas:

1. **WebSocketSubscribeListener not being triggered** - No logs from presence publishing
2. **Redis listeners not receiving messages** - Messages published but not listened to
3. **Principal/Authentication not being propagated** - Subscribe events lack user info

## New Diagnostic Logging Added

### 1. WebSocketConfig.java - SUBSCRIBE Event Logging

Now logs when STOMP SUBSCRIBE commands are processed:

```
🟢 STOMP SUBSCRIBE authenticated for user: jjj to: /topic/session/{id}/presence
```

Or if there's a problem:
```
❌ STOMP SUBSCRIBE rejected: Principal not found for subscription to: /topic/session/{id}/presence
```

### 2. WebSocketSubscribeListener.java - Detailed Event Flow

Now logs the complete event handling flow:

```
🟢 [WebSocketSubscribeListener] SUBSCRIBE EVENT RECEIVED
[WebSocketSubscribeListener] 📍 Destination: /topic/session/{id}/presence
[WebSocketSubscribeListener] 👤 Principal: jjj
[WebSocketSubscribeListener] 🔗 Connection ID: xxx
[WebSocketSubscribeListener] ✅ Matches /topic/session/ pattern
[WebSocketSubscribeListener] 📍 Extracted Session ID: {id}
[WebSocketSubscribeListener] 📤 Calling sessionService.userJoined()
🟢 [SessionService] USER JOINED EVENT
[SessionService] 📍 Session ID: {id}
[SessionService] 👤 User ID: jjj
[SessionService] 📤 Publishing to Redis...
```

## How to Run Diagnostics

### Step 1: Rebuild Backend
```bash
cd collabdebug-backend
mvn clean compile
```

### Step 2: Run Backend with Debug Logging
```bash
mvn spring-boot:run
```

Look for startup output showing Redis listeners are registered.

### Step 3: Test Scenario

**Browser 1:**
1. Login as "alice"
2. Create a new session "Test"
3. Wait for WebSocket to connect
4. Look at backend console

**Expected Output in Backend Console:**

```
🟢 STOMP CONNECT authenticated for user: alice
🟢 STOMP SUBSCRIBE authenticated for user: alice to: /topic/session/.../chat
🟢 STOMP SUBSCRIBE authenticated for user: alice to: /topic/session/.../edits
🟢 STOMP SUBSCRIBE authenticated for user: alice to: /topic/session/.../presence  ← KEY LOG
🟢 [WebSocketSubscribeListener] SUBSCRIBE EVENT RECEIVED
[WebSocketSubscribeListener] 📍 Destination: /topic/session/.../presence
[WebSocketSubscribeListener] 👤 Principal: alice
[WebSocketSubscribeListener] ✅ Matches /topic/session/ pattern
[WebSocketSubscribeListener] 📍 Extracted Session ID: {uuid}
[WebSocketSubscribeListener] 📤 Calling sessionService.userJoined()
🟢 [SessionService] USER JOINED EVENT
[SessionService] 👤 User ID: alice
[SessionService] 📤 Publishing to Redis channel: session-presence:{uuid}
[SessionService] ✅ Presence published successfully
```

**Step 4: Second User Joins**

**Browser 2:**
1. Login as "bob"
2. Join the same session "Test"

**Expected Output in Backend Console:**

```
🟢 STOMP CONNECT authenticated for user: bob
🟢 STOMP SUBSCRIBE authenticated for user: bob to: /topic/session/.../presence
🟢 [WebSocketSubscribeListener] SUBSCRIBE EVENT RECEIVED
[WebSocketSubscribeListener] 👤 Principal: bob
[WebSocketSubscribeListener] 📤 Calling sessionService.userJoined()
🟢 [SessionService] USER JOINED EVENT
[SessionService] 👤 User ID: bob
[SessionService] 📤 Publishing to Redis channel: session-presence:{uuid}

🟢 [PresenceListener] ✅ MESSAGE RECEIVED on channel: session-presence:{uuid}
[PresenceListener] ✅ Deserialized presence data: {type=joined, userId=bob}
[PresenceListener] 📤 Broadcasting to: /topic/session/{uuid}/presence
[PresenceListener] ✅ SUCCESSFULLY relayed presence update
```

## Troubleshooting Flowchart

```
Backend Starts
    ↓
    └─→ Look for: "All Redis message listeners registered successfully!" 
        If NOT found: ❌ RedisConfig not working
        If found: ✅ Continue
    
User Subscribes to /topic/session/.../presence
    ↓
    └─→ Look for: "🟢 STOMP SUBSCRIBE authenticated for user:"
        If NOT found: ❌ WebSocket config issue, check security
        If "❌ STOMP SUBSCRIBE rejected": ❌ Principal not authenticated
        If found with user name: ✅ Continue
    
WebSocketSubscribeListener processes event
    ↓
    └─→ Look for: "[WebSocketSubscribeListener] SUBSCRIBE EVENT RECEIVED"
        If NOT found: ❌ EventListener not triggered (Spring config issue)
        If found: ✅ Continue
        
    └─→ Look for: "[WebSocketSubscribeListener] 👤 Principal: {username}"
        If "Principal: NULL": ❌ Authentication not propagated
        If username shown: ✅ Continue
        
    └─→ Look for: "[WebSocketSubscribeListener] ✅ Matches /topic/session/"
        If "⏭️ Not a session topic": ❌ Path parsing issue
        If matches: ✅ Continue

SessionService publishes to Redis
    ↓
    └─→ Look for: "[SessionService] 📤 Publishing to Redis"
        If NOT found: ❌ userJoined() not called
        If found: ✅ Continue
        
    └─→ Look for: "[SessionService] ✅ Presence published successfully"
        If not found: ❌ Redis connection issue
        If found: ✅ Continue

PresenceListener receives from Redis
    ↓
    └─→ Look for: "[PresenceListener] ✅ MESSAGE RECEIVED"
        If NOT found: ❌ Redis listener not registered or Redis not working
        If found: ✅ Continue
        
    └─→ Look for: "[PresenceListener] ✅ SUCCESSFULLY relayed"
        If NOT found: ❌ WebSocket broadcasting issue
        If found: ✅ SUCCESS!

Frontend receives via WebSocket
    ↓
    └─→ Browser console: "[Presence] Received event: {type: 'joined'...}"
        If NOT found: ❌ WebSocket delivery issue
        If found: ✅ Participants list should update instantly!
```

## Key Checkpoints

### Checkpoint 1: Backend Startup
```bash
grep "All Redis message listeners registered" backend.log
```
Should find this line with 6 listeners mentioned.

### Checkpoint 2: User Connects
```bash
grep "STOMP CONNECT authenticated" backend.log
```
Should show user connecting.

### Checkpoint 3: User Subscribes to Presence
```bash
grep "STOMP SUBSCRIBE.*presence" backend.log
```
Should show subscription to presence topic.

### Checkpoint 4: Presence Event Published
```bash
grep "USER JOINED EVENT" backend.log
```
Should appear when user subscribes.

### Checkpoint 5: Redis Message Received
```bash
grep "PresenceListener.*MESSAGE RECEIVED" backend.log
```
Should appear after presence published.

### Checkpoint 6: WebSocket Broadcast
```bash
grep "SUCCESSFULLY relayed presence" backend.log
```
Should show message relayed to clients.

### Checkpoint 7: Frontend Receives
```javascript
// Browser console
[Presence] Received event:
```

## Common Issues and Fixes

### Issue 1: No "STOMP SUBSCRIBE" logs
**Cause:** SUBSCRIBE events not being logged by WebSocketConfig  
**Fix:** Verify WebSocketConfig channelInterceptor has SUBSCRIBE handler  
**Check:** Search for "STOMP SUBSCRIBE" in logs

### Issue 2: "Principal: NULL" in STOMP SUBSCRIBE
**Cause:** JWT not being properly set in session attributes  
**Fix:** Check JwtHandshakeInterceptor is setting "principal" attribute  
**Check:** Look for "STOMP CONNECT authenticated" - if that works, handshake is OK

### Issue 3: WebSocketSubscribeListener events not appearing
**Cause:** @EventListener not triggering for SessionSubscribeEvent  
**Fix:** Ensure WebSocketSubscribeListener is a @Component and in component scan  
**Check:** Look for Spring boot startup logs about WebSocketSubscribeListener

### Issue 4: "MESSAGE RECEIVED" not appearing
**Cause:** Redis listener not registered or message not published  
**Fix:** 
  1. Verify Redis is running: `redis-cli ping` → should return PONG
  2. Verify PresenceListener is registered in RedisConfig
  3. Verify presence is being published (check checkpoint 4)
**Check:** `grep "PresenceListener" backend.log`

### Issue 5: Frontend still doesn't see instant updates
**Cause:** WebSocket delivery not working  
**Fix:** Check browser WebSocket connection  
**Check:** Browser console for subscription logs

## Real-Time Monitoring

### Monitor Redis Messages (in another terminal)
```bash
redis-cli
> SUBSCRIBE "session-presence:*"
```

When users join/leave, you should see messages here.

### Monitor Backend Logs (follow in real-time)
```bash
tail -f backend.log | grep -E "SUBSCRIBE|USER JOINED|MESSAGE RECEIVED|SUCCESSFULLY"
```

### Monitor Frontend Logs (browser console)
```javascript
[Presence] Received event:
[Presence] After join:
```

## Success Indicators

✅ All of these should appear in logs when second user joins:

1. Backend receives STOMP SUBSCRIBE
2. WebSocketSubscribeListener processes it
3. SessionService publishes to Redis
4. PresenceListener receives from Redis
5. PresenceListener broadcasts to WebSocket
6. Frontend console shows [Presence] Received event
7. Participants list updates instantly in UI

If ANY step is missing, that's where the bug is!

## Next Steps

1. Run the backend with this enhanced logging
2. Test the join/leave scenario
3. Capture the backend console output
4. Share the output - the logs will show exactly where the flow breaks
5. We can then fix the specific issue
