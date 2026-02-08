package com.example.demo.controller;

import com.example.demo.model.UserSettings;
import com.example.demo.service.UserSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.Map;

@RestController
@RequestMapping("/api/user-settings")
@RequiredArgsConstructor
public class UserSettingsController {

    private final UserSettingsService userSettingsService;

    @GetMapping
    public UserSettings getSettings(Authentication auth) {
        return userSettingsService.getSettings(auth.getName());
    }

    @PostMapping
    public UserSettings saveSettings(Authentication auth, @RequestBody Map<String, String> body) {
        UserSettings settings = new UserSettings();
        settings.setUserName(auth.getName());
        String companyAffiliation = body.get("companyAffiliation");
        if (companyAffiliation != null && !companyAffiliation.isBlank())
            settings.setCompanyAffiliation(companyAffiliation);
        String section = body.get("section");
        if (section != null && !section.isBlank()) {
            try {
                settings.setSection(Integer.parseInt(section));
            } catch (NumberFormatException ignored) {
            }
        }
        String branchOffice = body.get("branchOffice");
        if (branchOffice != null && !branchOffice.isBlank()) settings.setBranchOffice(branchOffice);
        String workGroup = body.get("workGroup");
        if (workGroup != null && !workGroup.isBlank()) {
            try {
                settings.setWorkGroup(Integer.parseInt(workGroup));
            } catch (NumberFormatException ignored) {
            }
        }
        String employeeNumber = body.get("employeeNumber");
        if (employeeNumber != null && !employeeNumber.isBlank()) settings.setEmployeeNumber(employeeNumber);
        String siteRegularHours = body.get("siteRegularHours");
        if (siteRegularHours != null && !siteRegularHours.isBlank()) {
            try {
                settings.setSiteRegularHours(LocalTime.parse(siteRegularHours));
            } catch (Exception ignored) {
            }
        }
        String displayName = body.get("displayName");
        settings.setDisplayName(displayName != null && !displayName.isBlank() ? displayName : auth.getName());
        return userSettingsService.saveOrUpdate(settings);
    }
}
