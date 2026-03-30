package com.example.demo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@NoArgsConstructor
public class SaveUserSettingsRequest {
    @Size(max = 50)
    private String companyAffiliation;

    @Min(1)
    @Max(5)
    private Integer section;

    @Size(max = 50)
    private String branchOffice;

    @Min(1)
    @Max(5)
    private Integer workGroup;

    @Pattern(regexp = "^$|^[0-9]{1,20}$", message = "employeeNumber must be numeric and up to 20 digits")
    private String employeeNumber;

    private LocalTime siteRegularHours;

    @Size(max = 128)
    private String displayName;
}

