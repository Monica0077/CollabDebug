# Testing Guide: Redis Pub/Sub Presence Fix

## Quick Start Test

### Step 1: Start Backend
```bash
cd CollabDebug/collabdebug-backend
./mvnw spring-boot:run
```

Expected output in console:
```
[RedisConfig] ✅ All Redis message listeners registered successfully!
[RedisConfig] Listening to channels:
  - session-terminal:*
  - session-chat:*
  - session-updates:*
  - session-presence:* (NOW ACTIVE - FIX!)
  - session-meta:*
  - session-end:*
```

### Step 2: Start Frontend
```bash
cd CollabDebug/collabdebug-frontend
npm run dev
```

### Step 3: Test Scenario

**In Browser 1:**
1. Login with user `jjj`
2. Create a new session "Test Session"
3. Console should show WebSocket connected

**In Browser 2 (different user):**
1. Login with user `lll`
2. Join the same session "Test Session"
3. Verify **INSTANT** participant update in Browser 1

### What to Look For

#### In Backend Console:

When `lll` joins the session, you should see:
```
🟢 [SessionService] USER JOINED EVENT
[SessionService] 📍 Session ID: {uuid}
[SessionService] 👤 User ID: lll
[SessionService] 🔗 Connection ID: {connection-id}
[SessionService] 📤 Publishing to Redis channel: session-presence:{uuid}
[SessionService] ✅ Presence published successfully
================================================================================

🟢 [PresenceListener] ✅ MESSAGE RECEIVED on channel: session-presence:{uuid}
[PresenceListener] ✅ Deserialized presence data: {type=joined, userId=lll}
[PresenceListener] 📍 Session ID: {uuid}
[PresenceListener] 👤 Event Type: joined
[PresenceListener] 📝 User ID: lll
[PresenceListener] 📤 Broadcasting to: /topic/session/{uuid}/presence
[PresenceListener] ✅ SUCCESSFULLY relayed presence update to WebSocket clients
[PresenceListener] Complete data: {type=joined, userId=lll}
================================================================================
```

#### In Browser 1 Console:

```
[Presence] Received event: {
  type: "joined",
  userId: "lll",
  timestamp: "2024-11-14T10:30:45.123Z"
}
[Presence] After join - Current participants: ["jjj", "lll"]
```

#### In Browser 1 UI:

- ✅ Participants list shows both `jjj` and `lll` **INSTANTLY**
- ✅ System message appears: "lll joined the session"
- ✅ No 30-second delay!

---

## Detailed Testing Scenarios

### Scenario 1: User Joins
**Expected Behavior:**
- ✅ Participants list updates instantly (NOT after 30 seconds)
- ✅ System message "X joined the session" appears in chat
- ✅ Backend console shows presence published and relayed

### Scenario 2: User Leaves
**Expected Behavior:**
- ✅ Participants list updates instantly
- ✅ System message "X left the session" appears in chat
- ✅ Backend console shows:
  ```
  🔴 [SessionService] USER LEFT EVENT
  [SessionService] ✅ Presence published successfully
  ```

### Scenario 3: Multiple Users Join
**Setup:**
- 3 browsers open with different users (alice, bob, charlie)

**Expected Behavior:**
- ✅ Each join is visible instantly to all other participants
- ✅ All system messages appear instantly
- ✅ No race conditions or missing updates

### Scenario 4: Quick Join/Leave
**Setup:**
- User joins, immediately leaves

**Expected Behavior:**
- ✅ Both events processed and shown
- ✅ No participant is stuck in the list
- ✅ Backend handles cleanup correctly

---

## Debugging Checklist

If you don't see instant updates, check these:

### 1. Backend logs show all listeners registered ✓
```bash
grep "All Redis message listeners registered" logs
```

### 2. Presence listener is receiving messages ✓
```bash
grep "MESSAGE RECEIVED on channel: session-presence" logs
```

### 3. Messages are being relayed to WebSocket ✓
```bash
grep "SUCCESSFULLY relayed presence update" logs
```

### 4. Frontend is subscribed correctly ✓
```javascript
// Browser console should show:
console.log('[Presence] Received event:', ...)
```

### 5. Redis is running and connected ✓
```bash
redis-cli ping
# Should return: PONG
```

### 6. WebSocket connection is active ✓
```javascript
// In browser console:
stompClientRef.current?.connected
// Should return: true
```

---

## Performance Expectations

| Metric | Expected |
|--------|----------|
| Join notification delay | < 100ms |
| Participants list update | Instant |
| System message appearance | Instant |
| Network latency | ~50ms (localhost) |
| Redis pub/sub latency | ~1-2ms |

---

## Troubleshooting

### Issue: Participants still take 30 seconds to update

**Cause:** PresenceListener not receiving messages

**Solution:**
1. Verify `PresenceListener` is being instantiated:
   ```bash
   grep "PresenceListener" backend.log
   ```
2. Check Redis is running:
   ```bash
   redis-cli ping
   ```
3. Verify listener registration in RedisConfig:
   ```bash
   grep "session-presence:\*" backend.log
   ```

### Issue: Backend shows presence published but frontend doesn't receive

**Cause:** WebSocket subscription not active

**Solution:**
1. Check frontend WebSocket connection:
   ```javascript
   stompClientRef.current?.connected
   ```
2. Verify subscription:
   ```javascript
   client.subscribe(`/topic/session/${sessionId}/presence`, ...)
   ```
3. Check browser console for errors

### Issue: Error deserializing presence data

**Cause:** JSON serialization mismatch

**Solution:**
1. Check RedisTemplate serializers in RedisConfig
2. Verify `GenericJackson2JsonRedisSerializer` is set
3. Look for `@class` fields in Redis messages (should be handled)

---

## Performance Monitoring

### Monitor Redis Pub/Sub Activity
```bash
redis-cli
> MONITOR
```

### Check Active Listeners
```bash
# In backend logs, search for:
grep "registered successfully" logs
```

### Monitor WebSocket Connections
```javascript
// In browser:
Object.keys(stompClientRef.current)
// Should show active client with connected: true
```

---

## Success Criteria

✅ All 6 checklist items pass:
1. Participants list updates instantly (< 1 second)
2. System messages appear immediately
3. Backend logs show presence published and relayed
4. No 30-second delay
5. Multiple users work correctly
6. Join/leave events are consistent

If all pass, the Redis pub/sub fix is working correctly! 🎉
