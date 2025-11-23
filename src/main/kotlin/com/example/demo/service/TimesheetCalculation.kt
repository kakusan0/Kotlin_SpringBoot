package com.example.demo.service

import java.time.LocalTime

object TimesheetCalculation {
    data class CalcResult(val durationMinutes: Int?, val workingMinutes: Int?)

    fun compute(start: LocalTime?, end: LocalTime?, breakMinutes: Int?): CalcResult {
        val breakM = (breakMinutes ?: 0).coerceAtLeast(0)
        if (start == null || end == null) return CalcResult(null, null)
        val sTot = start.hour * 60 + start.minute
        val eTot = end.hour * 60 + end.minute
        val raw = if (eTot >= sTot) eTot - sTot else eTot + 1440 - sTot
        if (raw <= 0 || raw > 1440) return CalcResult(null, null)
        val working = (raw - breakM).coerceAtLeast(0)
        return CalcResult(raw, working)
    }
}

