package com.example.demo.controller;

import com.example.demo.dto.SaveUserSettingsRequest;
import com.example.demo.model.UserSettings;
import com.example.demo.service.UserSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;

@RestController
@RequestMapping("/api/user-settings")
@Validated
@RequiredArgsConstructor
public class UserSettingsController {

    private final UserSettingsService userSettingsService;


    @GetMapping
    public UserSettings getSettings(Authentication auth) {
        return userSettingsService.getSettings(auth.getName());
    }

    @PostMapping
    public UserSettings saveSettings(Authentication auth, @Valid @RequestBody SaveUserSettingsRequest body) {
        UserSettings settings = new UserSettings();
        settings.setUserName(auth.getName());
        String companyAffiliation = body.getCompanyAffiliation();
        if (companyAffiliation != null && !companyAffiliation.isBlank())
            settings.setCompanyAffiliation(companyAffiliation);
        if (body.getSection() != null) {
            settings.setSection(body.getSection());
        }
        String branchOffice = body.getBranchOffice();
        if (branchOffice != null && !branchOffice.isBlank()) settings.setBranchOffice(branchOffice);
        if (body.getWorkGroup() != null) {
            settings.setWorkGroup(body.getWorkGroup());
        }
        String employeeNumber = body.getEmployeeNumber();
        if (employeeNumber != null && !employeeNumber.isBlank()) settings.setEmployeeNumber(employeeNumber);
        LocalTime siteRegularHours = body.getSiteRegularHours();
        if (siteRegularHours != null) {
            settings.setSiteRegularHours(siteRegularHours);
        }
        String displayName = body.getDisplayName();
        settings.setDisplayName(displayName != null && !displayName.isBlank() ? displayName : auth.getName());
        return userSettingsService.saveOrUpdate(settings);
    }

}
