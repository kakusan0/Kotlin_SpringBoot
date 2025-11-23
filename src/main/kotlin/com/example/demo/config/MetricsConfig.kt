package com.example.demo.config

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

/**
 * Custom metrics configuration for timesheet functionality.
 * Uses @Component + @PostConstruct to avoid circular dependency with ObservationRegistry.
 */
@Component
class MetricsConfig(
    private val meterRegistry: MeterRegistry
) {
    private lateinit var clockInCounter: Counter
    private lateinit var clockOutCounter: Counter

    @PostConstruct
    fun initializeMetrics() {
        clockInCounter = Counter.builder("timesheet.clock.in.total")
            .description("Total number of clock-in operations")
            .register(meterRegistry)

        clockOutCounter = Counter.builder("timesheet.clock.out.total")
            .description("Total number of clock-out operations")
            .register(meterRegistry)
    }

    fun incrementClockIn() {
        clockInCounter.increment()
    }

    fun incrementClockOut() {
        clockOutCounter.increment()
    }
}
