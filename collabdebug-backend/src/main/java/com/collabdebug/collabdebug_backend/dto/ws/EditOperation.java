package com.collabdebug.collabdebug_backend.dto.ws;

public class EditOperation {
    public String opId;               // unique per op (UUID)
    public String rangeStart;         // or richer range data: {row,col}
    public String rangeEnd;
    public String text;               // inserted text (or empty for delete)
    public boolean isInsert;
}