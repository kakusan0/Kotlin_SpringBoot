package com.example.demo.service;

import java.time.LocalDate;

public record TimesheetUpdatedEvent(String userName, LocalDate date) {
}
