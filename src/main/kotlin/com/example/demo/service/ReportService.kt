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
            val sheet = wb.createSheet("勤務表")

            // Title rows: YearMonth 勤務表 / Company / Name
            val titleFont = wb.createFont().apply { bold = true; fontHeightInPoints = 14 }
            val titleStyle = wb.createCellStyle().apply { setFont(titleFont) }
            var rowIdx = 0
            val ymTitle = "${from.year}年${String.format("%02d", from.monthValue)}月　勤務表"
            sheet.createRow(rowIdx++).createCell(0).apply { setCellValue(ymTitle); cellStyle = titleStyle }
            sheet.createRow(rowIdx++).createCell(0).setCellValue("会社名　ユーニスイースト")
            sheet.createRow(rowIdx++).createCell(0).setCellValue("氏名　${username}")
            // empty row
            rowIdx++

            // Header
            val headerRow = sheet.createRow(rowIdx++)
            val headers = listOf("日付", "曜日", "出勤時間", "退勤時間", "休憩", "稼働時間", "実働")
            val headerFont = wb.createFont().apply { bold = true }
            val headerStyle = wb.createCellStyle().apply {
                setFont(headerFont)
                alignment = HorizontalAlignment.CENTER
                verticalAlignment = VerticalAlignment.CENTER
                fillForegroundColor = org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.index
                fillPattern = org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND
            }
            for (i in headers.indices) {
                headerRow.createCell(i).apply { setCellValue(headers[i]); cellStyle = headerStyle }
            }

            // styles
            val df = wb.creationHelper.createDataFormat()
            // day-only style (center)
            val dayOnlyStyle = wb.createCellStyle()
                .apply { alignment = HorizontalAlignment.CENTER; verticalAlignment = VerticalAlignment.CENTER }
            // centered text style for time strings
            val timeTextStyle = wb.createCellStyle()
                .apply { alignment = HorizontalAlignment.CENTER; verticalAlignment = VerticalAlignment.CENTER }
            val intStyle = wb.createCellStyle().apply {
                dataFormat = df.getFormat("#,##0")
                alignment = HorizontalAlignment.RIGHT
                verticalAlignment = VerticalAlignment.CENTER
            }
            wb.createCellStyle().apply { wrapText = true; verticalAlignment = VerticalAlignment.TOP }

            // fetch holidays between years
            val holidaySet = fetchHolidayDates(from.year, to.year)

            // Japanese weekday map
            val jpWeek = mapOf(
                java.time.DayOfWeek.MONDAY to "月",
                java.time.DayOfWeek.TUESDAY to "火",
                java.time.DayOfWeek.WEDNESDAY to "水",
                java.time.DayOfWeek.THURSDAY to "木",
                java.time.DayOfWeek.FRIDAY to "金",
                java.time.DayOfWeek.SATURDAY to "土",
                java.time.DayOfWeek.SUNDAY to "日"
            )

            var r = rowIdx
            // map entries by date for quick lookup
            val entryMap = entries.associateBy { it.workDate }
            var d = from
            while (!d.isAfter(to)) {
                val row = sheet.createRow(r++)
                val e = entryMap[d]

                // date as "〇日" string
                val dateCell = row.createCell(0)
                dateCell.setCellValue("${d.dayOfMonth}日")
                dateCell.cellStyle = dayOnlyStyle

                // weekday (Japanese)
                val wdCell = row.createCell(1)
                wdCell.setCellValue(jpWeek[d.dayOfWeek])

                // times as strings (HH:mm:ss)
                val sc = row.createCell(2)
                sc.setCellValue(e?.startTime?.toString() ?: "")
                val ec = row.createCell(3)
                ec.setCellValue(e?.endTime?.toString() ?: "")

                val breakCell = row.createCell(4)
                if (e?.breakMinutes != null) {
                    breakCell.setCellValue(e.breakMinutes.toDouble()); breakCell.cellStyle = intStyle
                } else breakCell.setCellValue("")

                val durCell = row.createCell(5)
                if (e?.durationMinutes != null) {
                    durCell.setCellValue(formatMinutesToHM(e.durationMinutes)); durCell.cellStyle = timeTextStyle
                } else durCell.setCellValue("")

                val workCell = row.createCell(6)
                if (e?.workingMinutes != null) {
                    workCell.setCellValue(formatMinutesToHM(e.workingMinutes)); workCell.cellStyle = timeTextStyle
                } else workCell.setCellValue("")

                // shade weekend/holiday across all used columns
                val isWeekend = d.dayOfWeek == java.time.DayOfWeek.SATURDAY || d.dayOfWeek == java.time.DayOfWeek.SUNDAY
                val isHoliday = holidaySet.contains(d)
                if (isWeekend || isHoliday) {
                    for (c in 0..6) {
                        val cell = row.getCell(c) ?: row.createCell(c)
                        val newStyle = wb.createCellStyle().apply {
                            cloneStyleFrom(cell.cellStyle ?: wb.createCellStyle())
                            fillForegroundColor = org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.index
                            fillPattern = org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND
                        }
                        cell.cellStyle = newStyle
                    }
                }

                d = d.plusDays(1)
            }

            // Try to auto-size columns. If POI provides the overload with useMergedCells, prefer that.
            for (i in 0..6) {
                try {
                    // reflection: call autoSizeColumn(int, boolean) if available to consider merged cells
                    val m =
                        sheet::class.java.methods.firstOrNull { it.name == "autoSizeColumn" && it.parameterCount == 2 }
                    if (m != null) {
                        // some POI versions accept a boolean second arg to consider merged regions
                        m.invoke(sheet, i, java.lang.Boolean.TRUE)
                    } else {
                        sheet.autoSizeColumn(i)
                    }
                } catch (ex: Exception) {
                    try {
                        sheet.autoSizeColumn(i)
                    } catch (_: Throwable) {
                        // ignore
                    }
                }
                var current = sheet.getColumnWidth(i)
                // fallback minimum widths (in character units * 256)
                val minChars = when (i) {
                    0 -> 6 // 日付
                    1 -> 4 // 曜日
                    2, 3 -> 10 // 時刻
                    4 -> 6 // 休憩
                    5, 6 -> 8 // 稼働/実働
                    else -> 8
                }
                val min = 256 * minChars
                if (current < min) current = min
                val max = 256 * 40
                if (current > max) current = max
                sheet.setColumnWidth(i, current)
            }

            // write workbook to bytes
            wb.write(baos)
        }
        return baos.toByteArray()
    }

    // utility: format minutes -> H:MM (e.g. 90 -> "1:30"), null -> empty string
    fun formatMinutesToHM(minutes: Int?): String {
        if (minutes == null) return ""
        val h = minutes / 60
        val m = minutes % 60
        return String.format("%d:%02d", h, m)
    }

    // fetch holiday dates between two years (inclusive). Best-effort: network errors are ignored and an empty set may be returned.
    private fun fetchHolidayDates(fromYear: Int, toYear: Int): Set<LocalDate> {
        val set = mutableSetOf<LocalDate>()
        for (y in fromYear..toYear) {
            try {
                val url = java.net.URL("https://date.nager.at/api/v3/PublicHolidays/$y/JP")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    // extract "date":"YYYY-MM-DD" occurrences without pulling in a JSON library
                    val re = """"date"\s*:\s*"(\d{4}-\d{2}-\d{2})"""".toRegex()
                    for (m in re.findAll(text)) {
                        val ds = m.groupValues[1]
                        try {
                            set.add(LocalDate.parse(ds))
                        } catch (_: Exception) {
                        }
                    }
                }
            } catch (_: Exception) {
                // ignore network/parse errors — best-effort
            }
        }
        return set
    }

}
