package com.example.demo.service;

public class TimesheetNotFoundException extends RuntimeException {
    public TimesheetNotFoundException(String message) {
        super(message);
    }
}
