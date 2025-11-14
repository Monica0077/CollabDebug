# The REAL Root Cause & Fix - Event Listener Timing Issue

## The Problem (That Fooled Us)

**Logs showed:**
- ✅ `🟢 STOMP SUBSCRIBE authenticated for user: jjj` (in WebSocketConfig)
- ❌ `👤 Principal: NULL` (in WebSocketSubscribeListener)

This looked like the principal was lost between the two points. But actually...

## The Real Issue: Message Processing Timeline

### Spring WebSocket Message Flow:

```
1. Client sends SUBSCRIBE message
    ↓
2. ChannelInterceptor.preSend() runs  ← WebSocketConfig.configureClientInboundChannel()
    - Sets principal via accessor.setUser()
    - Modifies the OUTGOING message
    ↓
3. EventListener fires with ORIGINAL message ← WebSocketSubscribeListener.handleSessionSubscribe()
    - Receives the UNMODIFIED input message
    - accessor.getUser() returns NULL (not set yet!)
    ↓
4. Modified message continues to broker
```

### Why This Happens:

- `preSend()` interceptor modifies the message **for the broker**
- `EventListener` is called with the **original message** from the client
- The interceptor's modifications haven't been applied to the event yet!

## The Solution: Use Session Attributes Instead

**Before (WRONG):**
```java
Principal principal = accessor.getUser();  // ❌ NULL - not set yet by interceptor
```

**After (CORRECT):**
```java
// Session attributes are set during HTTP handshake and persist throughout STOMP session
Principal principal = (Principal) accessor.getSessionAttributes().get("principal");  // ✅ Works!
```

### Why Session Attributes Work:

1. **JwtHandshakeInterceptor** sets principal in session attributes during HTTP handshake:
   ```java
   attributes.put("principal", user);  // Set here
   ```

2. **Session attributes persist** across all STOMP messages in that session

3. **EventListener can access them** immediately:
   ```java
   accessor.getSessionAttributes().get("principal")  // ✅ Available here
   ```

## Key Insight

The confusion arose because:
- We thought principal wasn't being passed from WebSocketConfig to EventListener
- Actually, the problem was using the wrong accessor method (`getUser()` vs session attributes)
- The preSend interceptor's modifications don't apply to the event that triggers immediately after

## Files Modified

### 1. WebSocketConfig.java
- ✅ Stores principal in session attributes on CONNECT command
- ✅ Sets accessor.setUser() for broker communication
- Added debug logging to show when principal is set

### 2. WebSocketSubscribeListener.java  
- ❌ **WRONG:** `Principal principal = accessor.getUser();`
- ✅ **CORRECT:** `Principal principal = (Principal) accessor.getSessionAttributes().get("principal");`
- Added debug logging to show available session attributes

### 3. JwtHandshakeInterceptor.java
- ✅ Stores principal in session attributes during HTTP handshake
- Added logging to confirm authentication

## Expected Behavior After Fix

```
✅ HTTP Handshake authenticated for user: jjj
   Principal stored in session attributes

🟢 STOMP CONNECT authenticated for user: jjj
   Principal retrieved from session attributes

🟢 STOMP SUBSCRIBE authenticated for user: jjj to: /topic/session/.../presence
   
🟢 [WebSocketSubscribeListener] SUBSCRIBE EVENT RECEIVED
[WebSocketSubscribeListener] 👤 Principal: jjj  ← NOW NOT NULL!
[WebSocketSubscribeListener] 📤 Calling sessionService.userJoined()
[WebSocketSubscribeListener] ✅ userJoined() completed

[Redis] Publishing presence event to channel: session-presence:...
```

## Impact on Real-Time Updates

With principal now available:
1. ✅ `sessionService.userJoined()` is called immediately when user subscribes
2. ✅ Presence event is published to Redis immediately
3. ✅ Redis listeners broadcast to all connected clients
4. ✅ Participants appear **instantly** (not after 30-second poll)

This fix enables the complete real-time chain!
