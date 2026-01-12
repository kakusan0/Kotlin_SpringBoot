package com.example.demo.model

import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * ユーザー設定（UNISS勤務表用）
 */
data class UserSettings(
    val id: Long? = null,
    val userName: String,
    val companyAffiliation: String? = null,  // 所属選択: ユーニスイースト, ユーニスウエスト
    val section: Int? = null,                // セクション: 1-5
    val branchOffice: String? = null,        // 支社: 東京支社, 名古屋支社, 大阪支社
    val workGroup: Int? = null,              // グループ: 1-5
    val employeeNumber: String? = null,      // 社員番号
    val siteRegularHours: LocalTime? = null, // 現場定時時間
    val displayName: String? = null,         // 氏名
    val createdAt: OffsetDateTime? = null,
    val updatedAt: OffsetDateTime? = null
)
