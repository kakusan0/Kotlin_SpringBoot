package com.example.demo.service

import java.time.LocalTime

@Suppress("unused")
class TimesheetValidationException(message: String) : RuntimeException(message)

@Suppress("unused")
data class TimesheetValidationResult(val errors: List<String>) {
    val isValid: Boolean get() = errors.isEmpty()
}

@Suppress("unused")
data class TimesheetEval(
    val durationMinutes: Int?,
    val workingMinutes: Int?,
    val errors: List<String>
) {
    val isValid: Boolean get() = errors.isEmpty()
}

object TimesheetEvaluator {
    // ルール定義: 最大勤務 12h (720分), 休憩は勤務の50%以内, 跨ぎは 24h 以内
    private const val MAX_WORK_DURATION_MIN = 720
    private const val MAX_CROSS_DURATION_MIN = 1440
    private const val BREAK_RATIO_LIMIT = 0.5 // 勤務の50%

    /**
     * start/end/break を検証し、妥当なら duration/working を計算。
     * 異常時は errors へ理由を格納し、計算可能なら duration/working も返す (一部不正でも部分表示許容)。
     */
    fun evaluate(start: LocalTime?, end: LocalTime?, breakMinutes: Int?): TimesheetEval {
        val errs = mutableListOf<String>()
        val breakM = (breakMinutes ?: 0)
        if (breakM < 0) errs += "休憩は0以上"
        var duration: Int? = null
        var working: Int? = null
        if (start != null && end != null) {
            val sTot = start.hour * 60 + start.minute
            val eTot = end.hour * 60 + end.minute
            duration = if (eTot >= sTot) eTot - sTot else eTot + 1440 - sTot
            // duration sanity
            if (duration <= 0) errs += "勤務時間が0以下"
            if (duration > MAX_CROSS_DURATION_MIN) errs += "勤務が24時間を超過"
            if (duration > MAX_WORK_DURATION_MIN) errs += "最大勤務(12h)超過"
            if (breakM > duration * BREAK_RATIO_LIMIT) errs += "休憩が勤務の50%を超過"
            if (breakM > duration) errs += "休憩が勤務時間を超過"
            if (duration in 1..MAX_CROSS_DURATION_MIN) {
                working = (duration - breakM).coerceAtLeast(0)
            }
        } else {
            if (breakM > 0) errs += "開始/終了未確定で休憩設定不可"
        }
        return TimesheetEval(duration, working, errs)
    }
}

@Suppress("unused")
object TimesheetValidator {
    @Suppress("unused")
    fun validate(start: LocalTime?, end: LocalTime?, breakMinutes: Int?): TimesheetValidationResult {
        val r = TimesheetEvaluator.evaluate(start, end, breakMinutes)
        return TimesheetValidationResult(r.errors)
    }
}
