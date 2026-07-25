package com.example.demo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record SaveUserSettingsRequest(
    @Size(max = 50)
    String companyAffiliation,

    @Min(1)
    @Max(5)
    Integer section,

    @Size(max = 50)
    String branchOffice,

    @Min(1)
    @Max(5)
    Integer workGroup,

    @Pattern(regexp = "^$|^[0-9]{1,20}$", message = "employeeNumber must be numeric and up to 20 digits")
    String employeeNumber,

    LocalTime siteRegularHours,

    @Size(max = 128)
    String displayName
) {}

