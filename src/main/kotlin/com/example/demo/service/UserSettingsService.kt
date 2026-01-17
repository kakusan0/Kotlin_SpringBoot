package com.example.demo.service

import com.example.demo.model.UserSettings
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Service
import java.sql.ResultSet

@Service
class UserSettingsService(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val cryptoService: CryptoService
) {

    companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(UserSettingsService::class.java)
    }

    /**
     * ユーザー設定を取得（存在しない場合はnull）
     */
    fun getSettings(userName: String): UserSettings? {
        val sql = """
            SELECT id, user_name, company_affiliation, section, branch_office, work_group,
                   employee_number, site_regular_hours, display_name, created_at, updated_at
            FROM user_settings
            WHERE user_name = :userName
        """.trimIndent()

        val params = MapSqlParameterSource("userName", userName)

        return try {
            jdbcTemplate.queryForObject(sql, params) { rs, _ -> mapRow(rs) }
        } catch (e: org.springframework.dao.EmptyResultDataAccessException) {
            null
        }
    }

    /**
     * ユーザー設定を保存または更新
     */
    fun saveOrUpdate(settings: UserSettings): UserSettings {
        val existing = getSettings(settings.userName)

        return if (existing == null) {
            insert(settings)
        } else {
            update(settings)
        }
    }

    /**
     * ユーザー設定を新規登録
     */
    private fun insert(settings: UserSettings): UserSettings {
        val sql = """
            INSERT INTO user_settings
            (user_name, company_affiliation, section, branch_office, work_group,
             employee_number, site_regular_hours, display_name)
            VALUES
            (:userName, :companyAffiliation, :section, :branchOffice, :workGroup,
             :employeeNumber, :siteRegularHours, :displayName)
        """.trimIndent()

        val params = MapSqlParameterSource().apply {
            addValue("userName", settings.userName)
            addValue("companyAffiliation", settings.companyAffiliation)
            addValue("section", settings.section)
            addValue("branchOffice", settings.branchOffice)
            addValue("workGroup", settings.workGroup)
            addValue("employeeNumber", cryptoService.encrypt(settings.employeeNumber))
            addValue("siteRegularHours", settings.siteRegularHours)
            addValue("displayName", cryptoService.encrypt(settings.displayName))
        }

        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(sql, params, keyHolder)

        return getSettings(settings.userName) ?: settings
    }

    /**
     * ユーザー設定を更新
     */
    private fun update(settings: UserSettings): UserSettings {
        val sql = """
            UPDATE user_settings
            SET company_affiliation = :companyAffiliation,
                section = :section,
                branch_office = :branchOffice,
                work_group = :workGroup,
                employee_number = :employeeNumber,
                site_regular_hours = :siteRegularHours,
                display_name = :displayName,
                updated_at = NOW()
            WHERE user_name = :userName
        """.trimIndent()

        val params = MapSqlParameterSource().apply {
            addValue("userName", settings.userName)
            addValue("companyAffiliation", settings.companyAffiliation)
            addValue("section", settings.section)
            addValue("branchOffice", settings.branchOffice)
            addValue("workGroup", settings.workGroup)
            addValue("employeeNumber", cryptoService.encrypt(settings.employeeNumber))
            addValue("siteRegularHours", settings.siteRegularHours)
            addValue("displayName", cryptoService.encrypt(settings.displayName))
        }

        jdbcTemplate.update(sql, params)

        return getSettings(settings.userName) ?: settings
    }

    /**
     * ResultSetからUserSettingsオブジェクトへマッピング
     */
    private fun mapRow(rs: ResultSet): UserSettings {
        return UserSettings(
            id = rs.getLong("id"),
            userName = rs.getString("user_name"),
            companyAffiliation = rs.getString("company_affiliation"),
            section = rs.getObject("section") as Int?,
            branchOffice = rs.getString("branch_office"),
            workGroup = rs.getObject("work_group") as Int?,
            employeeNumber = cryptoService.decrypt(rs.getString("employee_number")),
            siteRegularHours = rs.getTime("site_regular_hours")?.toLocalTime(),
            displayName = cryptoService.decrypt(rs.getString("display_name")),
            createdAt = rs.getTimestamp("created_at")?.toInstant()?.atOffset(java.time.ZoneOffset.UTC),
            updatedAt = rs.getTimestamp("updated_at")?.toInstant()?.atOffset(java.time.ZoneOffset.UTC)
        )
    }
}
