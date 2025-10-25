package com.collabdebug.collabdebug_backend.dto.ws;

public class EditResponse {
    public boolean applied;
    private String updatedText;
    private long serverVersion;

    public EditResponse(boolean applied, String updatedText, long serverVersion) {
        this.applied = applied;
        this.updatedText = updatedText;
        this.serverVersion = serverVersion;
    }

    // Getters and setters
    public boolean isSuccess() {
        return applied;
    }

    public void setSuccess(boolean success) {
        this.applied = success;
    }

    public String getUpdatedText() {
        return updatedText;
    }

    public void setUpdatedText(String updatedText) {
        this.updatedText = updatedText;
    }

    public long getServerVersion() {
        return serverVersion;
    }

    public void setServerVersion(long serverVersion) {
        this.serverVersion = serverVersion;
    }
}
