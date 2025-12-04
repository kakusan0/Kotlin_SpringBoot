package com.example.demo.controller

import com.example.demo.model.CalendarHoliday
import com.example.demo.service.CalendarHolidayService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/calendar")
class CalendarHolidayController(
    private val calendarHolidayService: CalendarHolidayService
) {

    /**
     * 指定年の祝日一覧を取得
     * GET /api/calendar/holidays?year=2025
     */
    @GetMapping("/holidays")
    fun getHolidays(@RequestParam year: Int): Map<String, String> {
        return calendarHolidayService.getHolidaysMapByYear(year)
    }

    /**
     * 指定年の祝日一覧を詳細形式で取得
     * GET /api/calendar/holidays/list?year=2025
     */
    @GetMapping("/holidays/list")
    fun getHolidaysList(@RequestParam year: Int): List<CalendarHoliday> {
        return calendarHolidayService.getHolidaysByYear(year)
    }

    /**
     * 日付範囲で祝日を取得
     * GET /api/calendar/holidays/range?from=2025-01-01&to=2025-12-31
     */
    @GetMapping("/holidays/range")
    fun getHolidaysByRange(
        @RequestParam from: String,
        @RequestParam to: String
    ): Map<String, String> {
        val fromDate = LocalDate.parse(from)
        val toDate = LocalDate.parse(to)
        val holidays = calendarHolidayService.getHolidaysByRange(fromDate, toDate)
        return holidays.associate { it.holidayDate.toString() to it.name }
    }

    /**
     * 祝日を追加
     * POST /api/calendar/holidays
     */
    @PostMapping("/holidays")
    fun addHoliday(
        auth: Authentication,
        @RequestBody body: Map<String, String>
    ): ResponseEntity<Map<String, Any>> {
        val dateStr = body["date"] ?: return ResponseEntity.badRequest()
            .body(mapOf("success" to false, "message" to "date is required"))
        val name = body["name"] ?: return ResponseEntity.badRequest()
            .body(mapOf("success" to false, "message" to "name is required"))

        return try {
            val date = LocalDate.parse(dateStr)
            val holiday = calendarHolidayService.addHoliday(date, name)
            ResponseEntity.ok(mapOf("success" to true, "holiday" to holiday))
        } catch (e: Exception) {
            ResponseEntity.badRequest()
                .body(mapOf("success" to false, "message" to (e.message ?: "Failed to add holiday")))
        }
    }

    /**
     * 祝日を削除
     * DELETE /api/calendar/holidays/{id}
     */
    @DeleteMapping("/holidays/{id}")
    fun deleteHoliday(
        auth: Authentication,
        @PathVariable id: Long
    ): ResponseEntity<Map<String, Any>> {
        return try {
            val deleted = calendarHolidayService.deleteHoliday(id)
            if (deleted > 0) {
                ResponseEntity.ok(mapOf("success" to true, "deleted" to deleted))
            } else {
                ResponseEntity.notFound().build()
            }
        } catch (e: Exception) {
            ResponseEntity.badRequest()
                .body(mapOf("success" to false, "message" to (e.message ?: "Failed to delete holiday")))
        }
    }
}

