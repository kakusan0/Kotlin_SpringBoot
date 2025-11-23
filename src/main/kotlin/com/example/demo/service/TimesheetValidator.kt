package com.example.demo.service

import java.time.LocalTime

class TimesheetValidationException(message: String) : RuntimeException(message)

data class TimesheetValidationResult(val errors: List<String>) {
    val isValid: Boolean get() = errors.isEmpty()
}

object TimesheetValidator {
    // ルール定義: 最大勤務 12h (720分), 休憩は勤務の50%以内, 跨ぎは 24h 以内
    private const val MAX_WORK_DURATION_MIN = 720
    private const val MAX_CROSS_DURATION_MIN = 1440
    private const val BREAK_RATIO_LIMIT = 0.5 // 勤務の50%

    fun validate(start: LocalTime?, end: LocalTime?, breakMinutes: Int?): TimesheetValidationResult {
        val errs = mutableListOf<String>()
        val breakM = breakMinutes ?: 0
        if (breakM < 0) errs += "休憩は0以上"
        if (start != null && end != null) {
            val sTot = start.hour * 60 + start.minute
            val eTot = end.hour * 60 + end.minute
            val duration = if (eTot >= sTot) eTot - sTot else eTot + 1440 - sTot
            if (duration <= 0) errs += "勤務時間が0以下"
            if (duration > MAX_CROSS_DURATION_MIN) errs += "勤務が24時間を超過"
            if (duration > MAX_WORK_DURATION_MIN) errs += "最大勤務(12h)超過"
            if (breakM > duration * BREAK_RATIO_LIMIT) errs += "休憩が勤務の50%を超過"
            if (breakM > duration) errs += "休憩が勤務時間を超過"
        } else if (breakM > 0) {
            errs += "開始/終了未確定で休憩設定不可"
        }
        return TimesheetValidationResult(errs)
    }
}
