package com.collabdebug.collabdebug_backend.dto.ws;

public class BaseMessage {
    public String type;          // "edit" | "chat" | "presence" | "heartbeat"
    public String sessionId;
    public String userId;
    public long clientVersion;   // client-side document version
    public long serverVersion;   // server-side assigned version (optional)
    public long timestamp;

    public String getSessionId() {
        return sessionId;
    }
    public String getUserId() {
        return userId;
    }

    public void setUserId(String id) {
        this.userId = id;
    }
}
