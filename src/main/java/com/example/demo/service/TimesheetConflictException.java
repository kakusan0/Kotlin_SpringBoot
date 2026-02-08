package com.example.demo.service;

public class TimesheetConflictException extends RuntimeException {
    public TimesheetConflictException(String message) {
        super(message);
    }
}
