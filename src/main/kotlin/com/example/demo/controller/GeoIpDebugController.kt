package com.example.demo.controller

import com.example.demo.service.GeoIpCountryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class GeoIpDebugController(
    private val geoIpCountryService: GeoIpCountryService
) {
    data class GeoIpStatus(
        val enabled: Boolean,
        val testIp: String?,
        val countryCode: String?
    )

    @GetMapping("/api/geoip/status")
    fun status(@RequestParam(required = false) ip: String?): GeoIpStatus {
        val enabled = geoIpCountryService.isEnabled()
        val testIp = ip ?: "1.1.1.1"
        val cc = if (enabled) geoIpCountryService.lookupCountryCode(testIp) else null
        return GeoIpStatus(enabled = enabled, testIp = testIp, countryCode = cc)
    }
}

