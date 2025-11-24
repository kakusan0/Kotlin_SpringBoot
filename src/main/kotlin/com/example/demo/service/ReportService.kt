package com.example.demo.service

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
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.time.LocalDate

@Service
class ReportService(
    private val timesheetService: TimesheetService
) {

    fun generateCsvBytes(username: String, from: LocalDate, to: LocalDate): ByteArray {
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
        return sb.toString().toByteArray(java.nio.charset.StandardCharsets.UTF_8)
    }

    fun generatePdfBytes(username: String, from: LocalDate, to: LocalDate): ByteArray {
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

    fun generateXlsxBytes(username: String, from: LocalDate, to: LocalDate): ByteArray {
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
}
