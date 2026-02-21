package com.example.demo.service;

import com.example.demo.model.UserSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CryptoService cryptoService;


    public UserSettings getSettings(String userName) {
        String sql = """
                    SELECT id, user_name, company_affiliation, section, branch_office, work_group,
                           employee_number, site_regular_hours, display_name, created_at, updated_at
                    FROM user_settings
                    WHERE user_name = :userName
                """;

        MapSqlParameterSource params = new MapSqlParameterSource("userName", userName);

        try {
            return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> mapRow(rs));
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public UserSettings saveOrUpdate(UserSettings settings) {
        UserSettings existing = getSettings(settings.getUserName());
        if (existing == null) {
            return insert(settings);
        }
        return update(settings);
    }

    private UserSettings insert(UserSettings settings) {
        String sql = """
                    INSERT INTO user_settings
                    (user_name, company_affiliation, section, branch_office, work_group,
                     employee_number, site_regular_hours, display_name)
                    VALUES
                    (:userName, :companyAffiliation, :section, :branchOffice, :workGroup,
                     :employeeNumber, :siteRegularHours, :displayName)
                """;

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("userName", settings.getUserName());
        params.addValue("companyAffiliation", settings.getCompanyAffiliation());
        params.addValue("section", settings.getSection());
        params.addValue("branchOffice", settings.getBranchOffice());
        params.addValue("workGroup", settings.getWorkGroup());
        params.addValue("employeeNumber", cryptoService.encrypt(settings.getEmployeeNumber()));
        params.addValue("siteRegularHours", settings.getSiteRegularHours());
        params.addValue("displayName", cryptoService.encrypt(settings.getDisplayName()));

        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder);

        UserSettings refreshed = getSettings(settings.getUserName());
        return refreshed != null ? refreshed : settings;
    }

    private UserSettings update(UserSettings settings) {
        String sql = """
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
                """;

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("userName", settings.getUserName());
        params.addValue("companyAffiliation", settings.getCompanyAffiliation());
        params.addValue("section", settings.getSection());
        params.addValue("branchOffice", settings.getBranchOffice());
        params.addValue("workGroup", settings.getWorkGroup());
        params.addValue("employeeNumber", cryptoService.encrypt(settings.getEmployeeNumber()));
        params.addValue("siteRegularHours", settings.getSiteRegularHours());
        params.addValue("displayName", cryptoService.encrypt(settings.getDisplayName()));

        jdbcTemplate.update(sql, params);

        UserSettings refreshed = getSettings(settings.getUserName());
        return refreshed != null ? refreshed : settings;
    }

    private UserSettings mapRow(ResultSet rs) throws SQLException {
        UserSettings settings = new UserSettings();
        long id = rs.getLong("id");
        settings.setId(rs.wasNull() ? null : id);
        settings.setUserName(rs.getString("user_name"));
        settings.setCompanyAffiliation(rs.getString("company_affiliation"));
        Object section = rs.getObject("section");
        settings.setSection(section != null ? (Integer) section : null);
        settings.setBranchOffice(rs.getString("branch_office"));
        Object workGroup = rs.getObject("work_group");
        settings.setWorkGroup(workGroup != null ? (Integer) workGroup : null);
        settings.setEmployeeNumber(cryptoService.decrypt(rs.getString("employee_number")));
        java.sql.Time siteTime = rs.getTime("site_regular_hours");
        settings.setSiteRegularHours(siteTime != null ? siteTime.toLocalTime() : null);
        settings.setDisplayName(cryptoService.decrypt(rs.getString("display_name")));
        java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            settings.setCreatedAt(createdAt.toInstant().atOffset(ZoneOffset.UTC));
        }
        java.sql.Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            settings.setUpdatedAt(updatedAt.toInstant().atOffset(ZoneOffset.UTC));
        }
        return settings;
    }
}
