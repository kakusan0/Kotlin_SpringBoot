package com.example.demo.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * 勤怠（日次）エントリ: 1ユーザ1日1レコード想定。
 * endTime が null の間は「勤務中」。
 */
data class TimesheetEntry(
    val id: Long? = null,
    val workDate: LocalDate,
    val userName: String,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val note: String? = null,
    val createdAt: OffsetDateTime? = null,
    val updatedAt: OffsetDateTime? = null,
)
