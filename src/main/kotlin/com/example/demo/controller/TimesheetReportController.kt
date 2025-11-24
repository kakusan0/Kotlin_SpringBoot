package com.example.demo.controller

import com.example.demo.service.ReportJobService
import com.example.demo.service.TimesheetService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.Principal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@RestController
@RequestMapping("/timesheet/report")
class TimesheetReportController(
    private val timesheetService: TimesheetService,
    private val reportJobService: ReportJobService,
    private val reportService: com.example.demo.service.ReportService
) {

    @GetMapping("/xlsx")
    fun xlsx(
        @RequestParam username: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        principal: Principal
    ): ResponseEntity<ByteArray> {
        if (principal.name != username) {
            val auth = org.springframework.security.core.context.SecurityContextHolder.getContext().authentication
            val hasAdmin = auth?.authorities?.any { it.authority == "ROLE_ADMIN" } ?: false
            if (!hasAdmin) return ResponseEntity.status(403).build()
        }
        val bytes = reportService.generateXlsxBytes(username, from, to)
        val headers = HttpHeaders()
        headers.contentType =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        val safeNameXlsx =
            URLEncoder.encode("timesheet_${username}_${from}_to_${to}.xlsx", StandardCharsets.UTF_8.toString())
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''$safeNameXlsx")
        return ResponseEntity.ok().headers(headers).body(bytes)
    }

    // Submit job endpoint for large ranges (only xlsx supported now)
    @GetMapping("/submit")
    fun submit(
        @RequestParam username: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam format: String,
        principal: Principal
    ): ResponseEntity<Any> {
        if (principal.name != username) {
            val auth = org.springframework.security.core.context.SecurityContextHolder.getContext().authentication
            val hasAdmin = auth?.authorities?.any { it.authority == "ROLE_ADMIN" } ?: false
            if (!hasAdmin) return ResponseEntity.status(403).build()
        }
        val days = ChronoUnit.DAYS.between(from, to) + 1
        if (days <= 31) {
            // only xlsx is supported for direct generation
            if (format.lowercase() != "xlsx") return ResponseEntity.badRequest().body("unsupported format")
            val bytes = reportService.generateXlsxBytes(username, from, to)
            val headers = HttpHeaders()
            headers.contentType =
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            val safeName =
                URLEncoder.encode("timesheet_${username}_${from}_to_${to}.xlsx", StandardCharsets.UTF_8.toString())
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''$safeName")
            return ResponseEntity.ok().headers(headers).body(bytes)
        }
        val jobId = reportJobService.submitJob(username, from, to, format)
        return ResponseEntity.accepted().body(mapOf("jobId" to jobId))
    }

    @GetMapping("/job/{id}")
    fun jobStatus(@PathVariable id: Long, principal: Principal): ResponseEntity<Any> {
        val job = reportJobService.getJob(id) ?: return ResponseEntity.notFound().build()
        if (principal.name != job.username) {
            val auth = org.springframework.security.core.context.SecurityContextHolder.getContext().authentication
            val hasAdmin = auth?.authorities?.any { it.authority == "ROLE_ADMIN" } ?: false
            if (!hasAdmin) return ResponseEntity.status(403).build()
        }
        val resp = mapOf(
            "id" to job.id,
            "status" to job.status,
            "filePath" to job.filePath,
            "errorMessage" to job.errorMessage
        )
        return ResponseEntity.ok(resp)
    }

    @GetMapping("/job/{id}/download")
    fun jobDownload(@PathVariable id: Long, principal: Principal): ResponseEntity<Any> {
        val job = reportJobService.getJob(id) ?: return ResponseEntity.notFound().build()
        if (principal.name != job.username) {
            val auth = org.springframework.security.core.context.SecurityContextHolder.getContext().authentication
            val hasAdmin = auth?.authorities?.any { it.authority == "ROLE_ADMIN" } ?: false
            if (!hasAdmin) return ResponseEntity.status(403).build()
        }
        if (job.status != "DONE" || job.filePath.isNullOrBlank()) return ResponseEntity.status(409)
            .body(mapOf("status" to job.status))
        val f = java.io.File(job.filePath!!)
        if (!f.exists()) return ResponseEntity.notFound().build()
        val bytes = f.readBytes()
        val headers = HttpHeaders()
        // force xlsx as only supported format for download
        headers.contentType =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        val safeName = URLEncoder.encode(f.name, StandardCharsets.UTF_8.toString())
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''$safeName")
        return ResponseEntity.ok().headers(headers).body(bytes)
    }

    // keep helper csvBytes if used elsewhere, but it's okay to keep for now
    fun csvBytes(username: String, from: LocalDate, to: LocalDate): ByteArray {
        val entries = timesheetService.list(username, from, to)
        val sb = StringBuilder()
        sb.append("work_date,start_time,end_time,break_minutes,duration_minutes,working_minutes,note\n")
        for (e in entries) {
            sb.append(e.workDate).append(',')
            sb.append(e.startTime?.toString() ?: "").append(',')
            sb.append(e.endTime?.toString() ?: "").append(',')
            sb.append(e.breakMinutes?.toString() ?: "").append(',')
            sb.append(e.durationMinutes?.toString() ?: "").append(',')
            sb.append(e.workingMinutes?.toString() ?: "").append(',')
            sb.append('"').append((e.note ?: "").replace('"', '"')).append('"').append('\n')
        }
        return sb.toString().toByteArray(StandardCharsets.UTF_8)
    }
}
