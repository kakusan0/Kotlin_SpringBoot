package com.example.demo.service

import java.time.LocalDate

data class TimesheetUpdatedEvent(val userName: String, val date: LocalDate)

