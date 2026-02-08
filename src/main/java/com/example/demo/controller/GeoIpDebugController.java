package com.example.demo.controller;

import com.example.demo.service.GeoIpCountryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GeoIpDebugController {

    private final GeoIpCountryService geoIpCountryService;

    public GeoIpDebugController(GeoIpCountryService geoIpCountryService) {
        this.geoIpCountryService = geoIpCountryService;
    }

    @GetMapping("/api/geoip/status")
    public GeoIpStatus status(@RequestParam(required = false) String ip) {
        boolean enabled = geoIpCountryService.isEnabled();
        String testIp = (ip != null && !ip.isBlank()) ? ip : "1.1.1.1";
        String cc = enabled ? geoIpCountryService.lookupCountryCode(testIp) : null;
        return new GeoIpStatus(enabled, testIp, cc);
    }

    public record GeoIpStatus(boolean enabled, String testIp, String countryCode) {
    }
}
