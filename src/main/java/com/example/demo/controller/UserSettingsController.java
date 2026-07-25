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
        String companyAffiliation = body.companyAffiliation();
        if (companyAffiliation != null && !companyAffiliation.isBlank())
            settings.setCompanyAffiliation(companyAffiliation);
        if (body.section() != null) {
            settings.setSection(body.section());
        }
        String branchOffice = body.branchOffice();
        if (branchOffice != null && !branchOffice.isBlank()) settings.setBranchOffice(branchOffice);
        if (body.workGroup() != null) {
            settings.setWorkGroup(body.workGroup());
        }
        String employeeNumber = body.employeeNumber();
        if (employeeNumber != null && !employeeNumber.isBlank()) settings.setEmployeeNumber(employeeNumber);
        LocalTime siteRegularHours = body.siteRegularHours();
        if (siteRegularHours != null) {
            settings.setSiteRegularHours(siteRegularHours);
        }
        String displayName = body.displayName();
        settings.setDisplayName(displayName != null && !displayName.isBlank() ? displayName : auth.getName());
        return userSettingsService.saveOrUpdate(settings);
    }

}
