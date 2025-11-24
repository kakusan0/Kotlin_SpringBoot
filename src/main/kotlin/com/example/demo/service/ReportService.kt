package com.example.demo.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

@Service
class ReportService(
    private val timesheetService: TimesheetService,
    @param:Value("\${report.holidayPosition:MIDDLE}")
    private val holidayPositionStr: String
) {

    // control where to put holiday column: START, MIDDLE, END
    private enum class HolidayPosition { START, MIDDLE, END }

    // resolve configured position (case-insensitive), default to MIDDLE if invalid
    private val holidayPosition: HolidayPosition = try {
        HolidayPosition.valueOf(holidayPositionStr.uppercase())
    } catch (_: Exception) {
        HolidayPosition.MIDDLE
    }

    // cache per year to avoid repeated API calls
    private val holidayCache: ConcurrentHashMap<Int, Map<LocalDate, String>> = ConcurrentHashMap()

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

            // Header — build based on configured holidayPosition
            val headerRow = sheet.createRow(rowIdx++)
            val baseBefore = listOf("日付", "曜日")
            val baseAfter = listOf("出勤時間", "退勤時間", "休憩", "稼働時間", "実働")
            val headers = when (holidayPosition) {
                HolidayPosition.START -> listOf("祝日") + baseBefore + baseAfter
                HolidayPosition.END -> baseBefore + baseAfter + listOf("祝日")
                else -> baseBefore + listOf("祝日") + baseAfter
            }
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

            // fetch holidays between years (map date -> holiday name)
            val holidayMap = fetchHolidayDates(from.year, to.year)

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

                // build cells based on headers positions
                val dateIdx = headers.indexOf("日付")
                val wdIdx = headers.indexOf("曜日")
                val holIdx = headers.indexOf("祝日")
                val scIdx = headers.indexOf("出勤時間")
                val ecIdx = headers.indexOf("退勤時間")
                val breakIdx = headers.indexOf("休憩")
                val durIdx = headers.indexOf("稼働時間")
                val workIdx = headers.indexOf("実働")

                // date as "〇日" string
                val dateCell = row.createCell(dateIdx)
                dateCell.setCellValue("${d.dayOfMonth}日")
                dateCell.cellStyle = dayOnlyStyle

                // weekday
                val wdCell = row.createCell(wdIdx)
                wdCell.setCellValue(jpWeek[d.dayOfWeek])

                // holiday name column
                val holCell = row.createCell(holIdx)
                val holidayName = holidayMap[d]
                if (holidayName != null) {
                    holCell.setCellValue(holidayName)
                } else {
                    holCell.setCellValue("")
                }

                // times as strings (HH:mm:ss)
                val sc = row.createCell(scIdx)
                sc.setCellValue(e?.startTime?.toString() ?: "")
                val ec = row.createCell(ecIdx)
                ec.setCellValue(e?.endTime?.toString() ?: "")

                val breakCell = row.createCell(breakIdx)
                if (e?.breakMinutes != null) {
                    breakCell.setCellValue(e.breakMinutes.toDouble()); breakCell.cellStyle = intStyle
                } else breakCell.setCellValue("")

                val durCell = row.createCell(durIdx)
                if (e?.durationMinutes != null) {
                    durCell.setCellValue(formatMinutesToHM(e.durationMinutes)); durCell.cellStyle = timeTextStyle
                } else durCell.setCellValue("")

                val workCell = row.createCell(workIdx)
                if (e?.workingMinutes != null) {
                    workCell.setCellValue(formatMinutesToHM(e.workingMinutes)); workCell.cellStyle = timeTextStyle
                } else workCell.setCellValue("")

                // shade weekend/holiday across all used columns
                val isWeekend = d.dayOfWeek == java.time.DayOfWeek.SATURDAY || d.dayOfWeek == java.time.DayOfWeek.SUNDAY
                val isHoliday = holidayMap.containsKey(d)
                if (isWeekend || isHoliday) {
                    // choose color: weekend -> gray, holiday -> soft pink; set text color accordingly
                    val fillColor =
                        if (isHoliday) org.apache.poi.ss.usermodel.IndexedColors.ROSE.index else org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.index
                    val fontForFill = wb.createFont().apply {
                        color =
                            if (isHoliday) org.apache.poi.ss.usermodel.IndexedColors.WHITE.index else org.apache.poi.ss.usermodel.IndexedColors.BLACK.index
                    }
                    for (c in 0 until headers.size) {
                        val cell = row.getCell(c) ?: row.createCell(c)
                        val newStyle = wb.createCellStyle().apply {
                            cloneStyleFrom(cell.cellStyle ?: wb.createCellStyle())
                            fillForegroundColor = fillColor
                            fillPattern = org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND
                            setFont(fontForFill)
                        }
                        cell.cellStyle = newStyle
                    }
                }

                d = d.plusDays(1)
            }

            // Try to auto-size columns. If POI provides the overload with useMergedCells, prefer that.
            for (i in 0 until headers.size) {
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
                } catch (_: Exception) {
                    try {
                        sheet.autoSizeColumn(i)
                    } catch (_: Throwable) {
                        // ignore
                    }
                }
                var current = sheet.getColumnWidth(i)
                // fallback minimum widths (in character units * 256)
                val minChars = when (i) {
                    headers.indexOf("日付") -> 6 // 日付
                    headers.indexOf("曜日") -> 4 // 曜日
                    headers.indexOf("祝日") -> 12 // 祝日名
                    headers.indexOf("出勤時間"), headers.indexOf("退勤時間") -> 10 // 時刻
                    headers.indexOf("休憩") -> 6 // 休憩
                    headers.indexOf("稼働時間"), headers.indexOf("実働") -> 8 // 稼働/実働
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

    // fetch holiday dates between two years (inclusive). Returns map LocalDate -> localName.
    // Uses Jackson for robust JSON parsing and caches results per year in-memory.
    private fun fetchHolidayDates(fromYear: Int, toYear: Int): Map<LocalDate, String> {
        val result = mutableMapOf<LocalDate, String>()
        val mapper = jacksonObjectMapper()
        for (y in fromYear..toYear) {
            // check cache first
            val cached = holidayCache[y]
            if (cached != null) {
                result.putAll(cached)
                continue
            }

            val yearMap = mutableMapOf<LocalDate, String>()
            try {
                val url = java.net.URI.create("https://date.nager.at/api/v3/PublicHolidays/$y/JP").toURL()
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    try {
                        // parse as list of maps
                        val list: List<Map<String, Any>> = mapper.readValue(text)
                        for (m in list) {
                            val ds = m["date"] as? String ?: continue
                            val name = (m["localName"] as? String) ?: (m["name"] as? String) ?: "祝日"
                            try {
                                yearMap[LocalDate.parse(ds)] = name
                            } catch (_: Exception) {
                            }
                        }
                    } catch (_: Exception) {
                        // best-effort parsing
                    }
                }
            } catch (_: Exception) {
                // ignore network/parse errors — keep best-effort
            }

            // cache the year's map (possibly empty)
            holidayCache[y] = yearMap
            result.putAll(yearMap)
        }

        return result
    }

}
