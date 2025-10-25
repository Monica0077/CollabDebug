package com.collabdebug.collabdebug_backend.dto.ws;

public class EditMessage extends BaseMessage {
    public EditOperation op;
    public String sessionId;
    public String userId;
    public long clientVersion;
    public long serverVersion;
}
