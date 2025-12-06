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
    // MyBatis の constructor mapping で java.lang.Integer を期待する場合があるため
    // ボックス型 (Int?) にしておく。デフォルトは既存と同じ 0 を保持。
    val version: Int? = 0,                   // 楽観ロック用バージョン
    // DBマイグレーションで追加されたフラグ列を反映
    val holidayWork: Boolean? = false,
    // 出社区分: "出社", "在宅", null
    val workLocation: String? = null
) {
    // 互換性のため、旧スキーマ(work_location カラムが無い)向けの13引数コンストラクタ
    // MyBatis は resultMap のカラム数に合わせてコンストラクタを探すため必要
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
        id,
        workDate,
        userName,
        startTime,
        endTime,
        note,
        createdAt,
        updatedAt,
        breakMinutes,
        durationMinutes,
        workingMinutes,
        version,
        holidayWork,
        null  // workLocation defaults to null
    )
}

