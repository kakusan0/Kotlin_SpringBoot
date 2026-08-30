package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "geoip")
public class GeoIpProperties {
    private String mmdbPath = "";
    private String allowedCountryCodes = "JP";
}
