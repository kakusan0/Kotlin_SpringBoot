package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "report")
public class ReportProperties {
    private String holidayPosition = "MIDDLE";
    private String template = "2026年5月度UNISS勤務表(6桁社員番号＋氏名).xlsx";
}
