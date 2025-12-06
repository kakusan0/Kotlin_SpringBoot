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
    val breakMinutes: Int? = null,          // 休憩(分)
    val durationMinutes: Int? = null,       // 稼働(開始〜終了, 分)
    val workingMinutes: Int? = null,        // 実働(稼働 - 休憩, 分)
    val version: Int? = 0,                   // 楽観ロック用バージョン
    val holidayWork: Boolean? = false,
    val workLocation: String? = null,        // 出社区分: "出社", "在宅"
    // 変則勤務
    val irregularWorkType: String? = null,   // 有給休暇, 特別休暇, 欠勤, 振替出勤, 振替休日, 休日出勤
    val irregularWorkDesc: String? = null,   // 変則勤務の説明
    val irregularWorkData: String? = null,   // 複数の変則勤務データ (JSON形式)
    // 遅刻
    val lateTime: String? = null,            // 遅刻時間 (例: "0:30")
    val lateDesc: String? = null,            // 遅刻の説明
    // 早退
    val earlyTime: String? = null,           // 早退時間 (例: "1:00")
    val earlyDesc: String? = null,           // 早退の説明
    // 有給消化
    val paidLeave: String? = null            // "有給" or null
) {
    // 互換性のため、旧スキーマ(14カラム: work_locationまで)向けのコンストラクタ
    constructor(
        id: Long?,
        workDate: LocalDate,
        userName: String,
        startTime: LocalTime?,
        endTime: LocalTime?,
        note: String?,
        createdAt: OffsetDateTime?,
        updatedAt: OffsetDateTime?,
        breakMinutes: Int?,
        durationMinutes: Int?,
        workingMinutes: Int?,
        version: Int?,
        holidayWork: Boolean?,
        workLocation: String?
    ) : this(
        id, workDate, userName, startTime, endTime, note, createdAt, updatedAt,
        breakMinutes, durationMinutes, workingMinutes, version, holidayWork, workLocation,
        null, null, null, null, null, null, null, null
    )

    // 互換性のため、旧スキーマ(13カラム: work_locationなし)向けのコンストラクタ
    constructor(
        id: Long?,
        workDate: LocalDate,
        userName: String,
        startTime: LocalTime?,
        endTime: LocalTime?,
        note: String?,
        createdAt: OffsetDateTime?,
        updatedAt: OffsetDateTime?,
        breakMinutes: Int?,
        durationMinutes: Int?,
        workingMinutes: Int?,
        version: Int?,
        holidayWork: Boolean?
    ) : this(
        id, workDate, userName, startTime, endTime, note, createdAt, updatedAt,
        breakMinutes, durationMinutes, workingMinutes, version, holidayWork,
        null, null, null, null, null, null, null, null, null
    )
}

