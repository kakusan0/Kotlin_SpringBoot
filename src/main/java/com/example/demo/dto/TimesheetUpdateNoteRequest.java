package com.example.demo.dto;

public record TimesheetUpdateNoteRequest(String note) {
    public TimesheetUpdateNoteRequest() {
        this(null);
    }
}
