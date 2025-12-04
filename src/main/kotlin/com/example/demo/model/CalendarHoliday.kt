package com.example.demo.model

import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * カレンダー祝日エントリ
 */
data class CalendarHoliday(
    val id: Long? = null,
    val holidayDate: LocalDate,
    val name: String,
    val year: Int,
    val createdAt: OffsetDateTime? = null,
    val updatedAt: OffsetDateTime? = null
)

