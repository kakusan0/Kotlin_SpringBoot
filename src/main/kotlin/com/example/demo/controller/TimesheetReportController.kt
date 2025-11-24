package com.example.demo.controller

import com.example.demo.service.ReportJobService
import com.example.demo.service.TimesheetService
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.Principal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@RestController
@RequestMapping("/timesheet/report")
class TimesheetReportController(
    private val timesheetService: TimesheetService,
    private val reportJobService: ReportJobService
) {

    @GetMapping("/csv")
    fun csv(
        @RequestParam username: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        principal: Principal
    ): ResponseEntity<ByteArray> {
        // Authorization: allow if same user or has ROLE_ADMIN
        if (principal.name != username) {
            // Check roles via SecurityContext if needed
            val auth = org.springframework.security.core.context.SecurityContextHolder.getContext().authentication
            val hasAdmin = auth?.authorities?.any { it.authority == "ROLE_ADMIN" } ?: false
            if (!hasAdmin) return ResponseEntity.status(403).build()
        }

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
        val bytes = sb.toString().toByteArray(StandardCharsets.UTF_8)
        val headers = HttpHeaders()
        headers.contentType = MediaType.parseMediaType("text/csv; charset=UTF-8")
        val safeName =
            URLEncoder.encode("timesheet_${username}_${from}_to_${to}.csv", StandardCharsets.UTF_8.toString())
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''$safeName")
        return ResponseEntity.ok().headers(headers).body(bytes)
    }

    @GetMapping("/pdf")
    fun pdf(
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
        val bytes = pdfBytes(username, from, to)
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_PDF
        val safeNamePdf =
            URLEncoder.encode("timesheet_${username}_${from}_to_${to}.pdf", StandardCharsets.UTF_8.toString())
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''$safeNamePdf")
        return ResponseEntity.ok().headers(headers).body(bytes)
    }

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
        val bytes = xlsxBytes(username, from, to)
        val headers = HttpHeaders()
        headers.contentType =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        val safeNameXlsx =
            URLEncoder.encode("timesheet_${username}_${from}_to_${to}.xlsx", StandardCharsets.UTF_8.toString())
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''$safeNameXlsx")
        return ResponseEntity.ok().headers(headers).body(bytes)
    }

    // New helper for ReportService usage
    fun pdfBytes(username: String, from: LocalDate, to: LocalDate): ByteArray {
        val entries = timesheetService.list(username, from, to)
        val baos = ByteArrayOutputStream()
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.LETTER)
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                val classLoader = Thread.currentThread().contextClassLoader
                val fontStream = classLoader.getResourceAsStream("fonts/NotoSansJP-Regular.ttf")
                    ?: classLoader.getResourceAsStream("fonts/NotoSansJP-Regular.otf")
                val unicodeFont: PDFont? = try {
                    fontStream?.use { PDType0Font.load(doc, it) }
                } catch (_: Throwable) {
                    null
                }
                val titleFont: PDFont? = unicodeFont ?: try {
                    PDType1Font::class.java.getField("HELVETICA_BOLD").get(null) as PDFont
                } catch (_: Throwable) {
                    try {
                        PDType1Font::class.java.getField("TIMES_BOLD").get(null) as PDFont
                    } catch (_: Throwable) {
                        null
                    }
                }
                val bodyFont: PDFont? = unicodeFont ?: try {
                    PDType1Font::class.java.getField("HELVETICA").get(null) as PDFont
                } catch (_: Throwable) {
                    try {
                        PDType1Font::class.java.getField("TIMES_ROMAN").get(null) as PDFont
                    } catch (_: Throwable) {
                        null
                    }
                }
                cs.beginText()
                if (titleFont != null) cs.setFont(titleFont, 14f)
                cs.newLineAtOffset(50f, 700f)
                cs.showText("Timesheet: $username  ${from} - ${to}")
                cs.endText()

                var y = 670f
                if (bodyFont != null) cs.setFont(bodyFont, 10f)
                cs.beginText()
                cs.newLineAtOffset(50f, y)
                cs.showText("Date       Start    End    Break  Duration  Working  Note")
                cs.endText()
                y -= 16f
                for (e in entries) {
                    if (y < 50f) {
                        cs.close()
                        val newPage = PDPage(PDRectangle.LETTER)
                        doc.addPage(newPage)
                        y = 700f
                    }
                    cs.beginText()
                    cs.newLineAtOffset(50f, y)
                    val line = String.format(
                        "%s  %s  %s  %s  %s  %s  %s",
                        e.workDate,
                        e.startTime?.toString() ?: "",
                        e.endTime?.toString() ?: "",
                        e.breakMinutes?.toString() ?: "",
                        e.durationMinutes?.toString() ?: "",
                        e.workingMinutes?.toString() ?: "",
                        (e.note ?: "")
                    )
                    cs.showText(line)
                    cs.endText()
                    y -= 14f
                }
            }
            doc.save(baos)
        }
        return baos.toByteArray()
    }

    fun xlsxBytes(username: String, from: LocalDate, to: LocalDate): ByteArray {
        val entries = timesheetService.list(username, from, to)
        val baos = ByteArrayOutputStream()
        XSSFWorkbook().use { wb ->
            val sheet = wb.createSheet("Timesheet")
            val header = sheet.createRow(0)
            val headers = listOf(
                "work_date",
                "start_time",
                "end_time",
                "break_minutes",
                "duration_minutes",
                "working_minutes",
                "note"
            )
            val headerFont = wb.createFont().apply { bold = true }
            val headerStyle = wb.createCellStyle().apply {
                setFont(headerFont)
                alignment = HorizontalAlignment.CENTER
                verticalAlignment = VerticalAlignment.CENTER
                fillForegroundColor = org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.index
                fillPattern = org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND
            }
            for (i in headers.indices) {
                val cell = header.createCell(i)
                cell.setCellValue(headers[i])
                cell.cellStyle = headerStyle
            }
            sheet.createFreezePane(0, 1)
            val df = wb.creationHelper.createDataFormat()
            val dateStyle = wb.createCellStyle().apply { dataFormat = df.getFormat("yyyy-mm-dd") }
            val intStyle = wb.createCellStyle().apply {
                dataFormat = df.getFormat("#,##0")
                alignment = HorizontalAlignment.RIGHT
                verticalAlignment = VerticalAlignment.CENTER
            }
            val noteStyle = wb.createCellStyle().apply {
                wrapText = true
                verticalAlignment = VerticalAlignment.TOP
            }
            var r = 1
            for (e in entries) {
                val row = sheet.createRow(r++)
                val dateCell = row.createCell(0)
                dateCell.setCellValue(java.sql.Date.valueOf(e.workDate))
                dateCell.cellStyle = dateStyle
                row.createCell(1).setCellValue(e.startTime?.toString() ?: "")
                row.createCell(2).setCellValue(e.endTime?.toString() ?: "")
                val breakCell = row.createCell(3)
                if (e.breakMinutes != null) {
                    breakCell.setCellValue(e.breakMinutes.toDouble()); breakCell.cellStyle = intStyle
                } else breakCell.setCellValue("")
                val durCell = row.createCell(4)
                if (e.durationMinutes != null) {
                    durCell.setCellValue(e.durationMinutes.toDouble()); durCell.cellStyle = intStyle
                } else durCell.setCellValue("")
                val workCell = row.createCell(5)
                if (e.workingMinutes != null) {
                    workCell.setCellValue(e.workingMinutes.toDouble()); workCell.cellStyle = intStyle
                } else workCell.setCellValue("")
                val noteCell = row.createCell(6)
                noteCell.setCellValue(e.note ?: "")
                noteCell.cellStyle = noteStyle
            }
            for (i in 0 until headers.size) {
                sheet.autoSizeColumn(i)
                val current = sheet.getColumnWidth(i)
                val min = 256 * 10
                if (current < min) sheet.setColumnWidth(i, min)
            }
            wb.write(baos)
        }
        return baos.toByteArray()
    }

    // Submit job endpoint for large ranges (or generate synchronously for small ranges)
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
            val bytes = when (format.lowercase()) {
                "csv" -> csvBytes(username, from, to)
                "pdf" -> pdfBytes(username, from, to)
                "xlsx" -> xlsxBytes(username, from, to)
                else -> return ResponseEntity.badRequest().body("unsupported format")
            }
            val headers = HttpHeaders()
            headers.contentType = when (format.lowercase()) {
                "csv" -> MediaType.parseMediaType("text/csv; charset=UTF-8")
                "pdf" -> MediaType.APPLICATION_PDF
                else -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            }
            val safeName =
                URLEncoder.encode("timesheet_${username}_${from}_to_${to}.${format}", StandardCharsets.UTF_8.toString())
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
        val ext = when (job.format.lowercase()) {
            "csv" -> "csv"; "pdf" -> "pdf"; else -> "xlsx"
        }
        val mime = when (ext) {
            "csv" -> MediaType.parseMediaType("text/csv; charset=UTF-8")
            "pdf" -> MediaType.APPLICATION_PDF
            else -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        }
        headers.contentType = mime
        val safeName = URLEncoder.encode(f.name, StandardCharsets.UTF_8.toString())
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''$safeName")
        return ResponseEntity.ok().headers(headers).body(bytes)
    }

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
