package com.example.demo.service

import org.junit.jupiter.api.Test
import java.time.LocalDate

class TimesheetServiceTest {
    @Test
    fun testList() {
        val timesheetService = object : com.example.demo.service.TimesheetService(null as Any?, null as Any?) {
            override fun list(
                userName: String,
                from: LocalDate,
                to: LocalDate
            ): List<com.example.demo.model.TimesheetEntry> {
                val list = mutableListOf<com.example.demo.model.TimesheetEntry>()
                var d = from
                while (!d.isAfter(to)) {
                    list.add(
                        com.example.demo.model.TimesheetEntry(
                            workDate = d,
                            userName = userName,
                            startTime = java.time.LocalTime.of(9, 0),
                            endTime = java.time.LocalTime.of(18, 0),
                            breakMinutes = 60,
                            note = if (d.dayOfMonth % 5 == 0) "Long note sample that might wrap" else ""
                        )
                    )
                    d = d.plusDays(1)
                }
                return list
            }
        }
        val list = timesheetService.list("smokeuser", LocalDate.of(2025, 11, 1), LocalDate.of(2025, 11, 30))
        list.forEach {
            println(it)
        }
    }
}
