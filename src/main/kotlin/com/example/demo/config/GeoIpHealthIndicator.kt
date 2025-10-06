package com.example.demo.config

import com.example.demo.service.GeoIpCountryService
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component

@Component
class GeoIpHealthIndicator(
    private val geoIpCountryService: GeoIpCountryService
) : HealthIndicator {
    override fun health(): Health {
        return if (geoIpCountryService.isEnabled()) {
            Health.up()
                .withDetail("geoip", "enabled")
                .build()
        } else {
            Health.down()
                .withDetail("geoip", "disabled or db not found")
                .build()
        }
    }
}

