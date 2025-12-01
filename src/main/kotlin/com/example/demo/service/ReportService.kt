package com.example.demo.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

@Service
class ReportService(
    private val timesheetService: TimesheetService,
    @param:Value("\${report.holidayPosition:MIDDLE}")
    private val holidayPositionStr: String
) {

    companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(ReportService::class.java)
    }

    private enum class HolidayPosition { START, MIDDLE, END }

    private val holidayPosition: HolidayPosition = try {
        HolidayPosition.valueOf(holidayPositionStr.uppercase())
    } catch (_: Exception) {
        HolidayPosition.MIDDLE
    }

    private val holidayCache: ConcurrentHashMap<Int, Map<LocalDate, String>> = ConcurrentHashMap()

    // XLSX generator (keeps previous behavior: holiday column placement, styles, autosize)
    fun generateXlsxBytes(username: String, from: LocalDate, to: LocalDate): ByteArray {
        val entries = timesheetService.list(username, from, to)
        val baos = ByteArrayOutputStream()
        XSSFWorkbook().use { wb ->
            val baseBefore = listOf("日付", "曜日")
            val baseAfter = listOf("出勤時間", "退勤時間", "休憩", "稼働時間", "実働")
            val headers = when (holidayPosition) {
                HolidayPosition.START -> listOf("祝日") + baseBefore + baseAfter
                HolidayPosition.END -> baseBefore + baseAfter + listOf("祝日")
                else -> baseBefore + listOf("祝日") + baseAfter
            }

            val sheet = wb.createSheet(username)

            val titleFont = wb.createFont().apply { bold = true; fontHeightInPoints = 14 }
            val titleStyle = wb.createCellStyle().apply {
                setFont(titleFont); alignment = HorizontalAlignment.CENTER; verticalAlignment = VerticalAlignment.CENTER
            }
            var rowIdx = 0
            val ymTitle = "${from.year}年${String.format("%02d", from.monthValue)}月度　勤務表".replace('　', ' ')
            val cols = headers.size
            val titleRow = sheet.createRow(rowIdx++)
            titleRow.createCell(0).apply { setCellValue(ymTitle); cellStyle = titleStyle }
            if (cols > 1) sheet.addMergedRegion(CellRangeAddress(0, 0, 0, cols - 1))

            sheet.createRow(rowIdx++) // blank row

            val infoFont = wb.createFont().apply { underline = org.apache.poi.ss.usermodel.Font.U_SINGLE }
            val leftStyle = wb.createCellStyle().apply {
                alignment = HorizontalAlignment.LEFT; verticalAlignment = VerticalAlignment.CENTER; setFont(infoFont)
            }
            val rightStyle = wb.createCellStyle().apply {
                alignment = HorizontalAlignment.RIGHT; verticalAlignment = VerticalAlignment.CENTER; setFont(infoFont)
            }
            val infoRow = sheet.createRow(rowIdx++)
            infoRow.createCell(0).apply { setCellValue("会社名：ユーニスイースト株式会社"); cellStyle = leftStyle }
            infoRow.createCell(maxOf(0, cols - 1)).apply { setCellValue("氏名：${username}"); cellStyle = rightStyle }
            sheet.createRow(rowIdx++) // empty row

            val headerRow = sheet.createRow(rowIdx++)
            val headerFont = wb.createFont().apply { bold = true }
            val headerStyle = wb.createCellStyle().apply {
                setFont(headerFont)
                alignment = HorizontalAlignment.CENTER
                verticalAlignment = VerticalAlignment.CENTER
                fillForegroundColor = org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.index
                fillPattern = org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND
                borderTop = org.apache.poi.ss.usermodel.BorderStyle.THIN
                borderBottom = org.apache.poi.ss.usermodel.BorderStyle.THIN
                borderLeft = org.apache.poi.ss.usermodel.BorderStyle.THIN
                borderRight = org.apache.poi.ss.usermodel.BorderStyle.THIN
            }
            for (i in headers.indices) headerRow.createCell(i)
                .apply { setCellValue(headers[i]); cellStyle = headerStyle }

            val df = wb.creationHelper.createDataFormat()
            val baseCellStyle = wb.createCellStyle().apply {
                verticalAlignment = VerticalAlignment.CENTER
                alignment = HorizontalAlignment.CENTER
                borderTop = org.apache.poi.ss.usermodel.BorderStyle.THIN
                borderBottom = org.apache.poi.ss.usermodel.BorderStyle.THIN
                borderLeft = org.apache.poi.ss.usermodel.BorderStyle.THIN
                borderRight = org.apache.poi.ss.usermodel.BorderStyle.THIN
            }
            val dayOnlyStyle =
                wb.createCellStyle().apply { cloneStyleFrom(baseCellStyle); alignment = HorizontalAlignment.CENTER }
            val timeTextStyle =
                wb.createCellStyle().apply { cloneStyleFrom(baseCellStyle); alignment = HorizontalAlignment.CENTER }
            val intStyle = wb.createCellStyle().apply {
                cloneStyleFrom(baseCellStyle); dataFormat = df.getFormat("#,##0"); alignment =
                HorizontalAlignment.CENTER
            }
            val defaultTextStyle =
                wb.createCellStyle().apply { cloneStyleFrom(baseCellStyle); alignment = HorizontalAlignment.CENTER }

            val holidayMap = fetchHolidayDates(from.year, to.year)
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
            val entryMap = entries.associateBy { it.workDate }
            var d = from
            while (!d.isAfter(to)) {
                val row = sheet.createRow(r++)
                val e = entryMap[d]

                val dateIdx = headers.indexOf("日付")
                val wdIdx = headers.indexOf("曜日")
                val holIdx = headers.indexOf("祝日")
                val scIdx = headers.indexOf("出勤時間")
                val ecIdx = headers.indexOf("退勤時間")
                val breakIdx = headers.indexOf("休憩")
                val durIdx = headers.indexOf("稼働時間")
                val workIdx = headers.indexOf("実働")

                val dateCell = row.createCell(dateIdx)
                dateCell.setCellValue("${d.dayOfMonth}日")
                dateCell.cellStyle = dayOnlyStyle

                val wdCell = row.createCell(wdIdx)
                wdCell.setCellValue(jpWeek[d.dayOfWeek])
                wdCell.cellStyle = dayOnlyStyle

                val holCell = row.createCell(holIdx)
                val holidayName = holidayMap[d]
                if (holidayName != null) holCell.setCellValue(holidayName) else holCell.setCellValue("")
                holCell.cellStyle = defaultTextStyle

                // 休日出勤フラグがオンの場合は時間関連セルを空文字にする
                val isHolidayWork = e?.holidayWork == true
                // 判定: その日が祝日かどうか、週末かどうかを分けて計算
                val isActualHoliday = holidayMap.containsKey(d)
                val isWeekend =
                    (d.dayOfWeek == java.time.DayOfWeek.SATURDAY || d.dayOfWeek == java.time.DayOfWeek.SUNDAY)
                val isHoliday = isActualHoliday || isWeekend
                // Excel出力で空欄にするか: 休日(祝日 or 週末)かつ休日出勤フラグがOFF の場合は空欄にする
                val shouldBlank = isHoliday && !isHolidayWork

                val sc = row.createCell(scIdx)
                if (shouldBlank) {
                    sc.setCellValue("")
                } else {
                    sc.setCellValue(e?.startTime?.toString() ?: "")
                }
                sc.cellStyle = timeTextStyle

                val ec = row.createCell(ecIdx)
                if (shouldBlank) {
                    ec.setCellValue("")
                } else {
                    ec.setCellValue(e?.endTime?.toString() ?: "")
                }
                ec.cellStyle = timeTextStyle

                val breakCell = row.createCell(breakIdx)
                if (shouldBlank) {
                    breakCell.setCellValue("")
                    breakCell.cellStyle = intStyle
                } else if (e?.breakMinutes != null) {
                    breakCell.setCellValue(e.breakMinutes.toDouble())
                    breakCell.cellStyle = intStyle
                } else {
                    breakCell.setCellValue("")
                    breakCell.cellStyle = intStyle
                }

                val durCell = row.createCell(durIdx)
                if (shouldBlank) {
                    durCell.setCellValue("")
                } else if (e?.durationMinutes != null) {
                    durCell.setCellValue(formatMinutesToHM(e.durationMinutes))
                } else {
                    durCell.setCellValue("")
                }
                durCell.cellStyle = timeTextStyle

                val workCell = row.createCell(workIdx)
                if (shouldBlank) {
                    workCell.setCellValue("")
                } else if (e?.workingMinutes != null) {
                    workCell.setCellValue(formatMinutesToHM(e.workingMinutes))
                } else {
                    workCell.setCellValue("")
                }
                workCell.cellStyle = timeTextStyle

                // fill weekend/holiday colors
                if (isHoliday) {
                    // determine if we should use the 'red' color: true for actual holidays and Sundays
                    val isRed = isActualHoliday || d.dayOfWeek == java.time.DayOfWeek.SUNDAY
                    val fillColor =
                        if (isRed) org.apache.poi.ss.usermodel.IndexedColors.ROSE.index else org.apache.poi.ss.usermodel.IndexedColors.LIGHT_CORNFLOWER_BLUE.index
                    val fontForFill = wb.createFont().apply {
                        color =
                            if (isRed) org.apache.poi.ss.usermodel.IndexedColors.WHITE.index else org.apache.poi.ss.usermodel.IndexedColors.BLACK.index
                    }
                    for (c in 0 until headers.size) {
                        val cell = row.getCell(c) ?: row.createCell(c)
                        val src = cell.cellStyle ?: baseCellStyle
                        val newStyle = wb.createCellStyle().apply {
                            cloneStyleFrom(src); fillForegroundColor = fillColor; fillPattern =
                            org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND; setFont(fontForFill)
                        }
                        cell.cellStyle = newStyle
                    }
                }

                d = d.plusDays(1)
            }

            // autosize columns with reasonable min/max
            for (i in 0 until headers.size) {
                try {
                    sheet.autoSizeColumn(i)
                } catch (_: Throwable) {
                }
                var current = sheet.getColumnWidth(i)
                val minChars = when (i) {
                    headers.indexOf("日付") -> 6
                    headers.indexOf("曜日") -> 4
                    headers.indexOf("祝日") -> 12
                    headers.indexOf("出勤時間"), headers.indexOf("退勤時間") -> 10
                    headers.indexOf("休憩") -> 6
                    headers.indexOf("稼働時間"), headers.indexOf("実働") -> 8
                    else -> 8
                }
                val min = 256 * minChars
                if (current < min) current = min
                val max = 256 * 40
                if (current > max) current = max
                sheet.setColumnWidth(i, current)
            }

            wb.write(baos)
        }
        return baos.toByteArray()
    }

    // minutes to H:MM
    fun formatMinutesToHM(minutes: Int?): String {
        if (minutes == null) return ""
        val h = minutes / 60
        val m = minutes % 60
        return String.format("%d:%02d", h, m)
    }

    // fetch holidays via Nager.Date and cache per year (returns map LocalDate -> localName)
    private fun fetchHolidayDates(fromYear: Int, toYear: Int): Map<LocalDate, String> {
        val result = mutableMapOf<LocalDate, String>()
        val mapper = jacksonObjectMapper()
        val client = HttpClient.newBuilder().build()
        for (y in fromYear..toYear) {
            val cached = holidayCache[y]
            if (cached != null) {
                result.putAll(cached); continue
            }
            try {
                val uri = URI.create("https://date.nager.at/api/v3/PublicHolidays/$y/JP")
                val req = HttpRequest.newBuilder().uri(uri).GET().build()
                val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
                if (resp.statusCode() != 200) {
                    logger.warn("Holiday API returned {} for year {}", resp.statusCode(), y); holidayCache[y] =
                        emptyMap(); continue
                }
                val root: JsonNode = mapper.readTree(resp.body())
                val mapForYear = mutableMapOf<LocalDate, String>()
                if (root.isArray) {
                    for (node in root) {
                        val dateStr = node.get("date")?.asText()
                        val localName = node.get("localName")?.asText() ?: node.get("name")?.asText() ?: ""
                        if (!dateStr.isNullOrBlank()) {
                            try {
                                val ld = LocalDate.parse(dateStr); mapForYear[ld] = localName
                            } catch (e: Exception) {
                                logger.warn("Failed to parse holiday date '{}' for year {}: {}", dateStr, y, e.message)
                            }
                        }
                    }
                }
                holidayCache[y] = mapForYear
                result.putAll(mapForYear)
            } catch (e: Exception) {
                logger.warn("Failed to fetch holidays for year {}: {}", y, e.message)
                holidayCache[y] = emptyMap()
            }
        }
        return result
    }

    // PDF generator mirroring XLSX structure
    fun generatePdfBytes(username: String, from: LocalDate, to: LocalDate): ByteArray {
        val entries = timesheetService.list(username, from, to)
        val baos = ByteArrayOutputStream()
        try {
            PDDocument().use { doc ->
                val fontStream = javaClass.classLoader.getResourceAsStream("fonts/KazukiReiwa - Bold.ttf")
                val font = fontStream?.let { PDType0Font.load(doc, it, true) }
                    ?: PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN)
                val headers = listOf("備考", "日付", "曜日", "出勤時間", "退勤時間", "休憩", "稼働時間", "実働")
                val colWidths = floatArrayOf(80f, 50f, 40f, 60f, 60f, 40f, 60f, 60f)
                val tableWidth = colWidths.sum()
                val holidayMap = fetchHolidayDates(from.year, to.year)
                val jpWeek = mapOf(
                    java.time.DayOfWeek.MONDAY to "月",
                    java.time.DayOfWeek.TUESDAY to "火",
                    java.time.DayOfWeek.WEDNESDAY to "水",
                    java.time.DayOfWeek.THURSDAY to "木",
                    java.time.DayOfWeek.FRIDAY to "金",
                    java.time.DayOfWeek.SATURDAY to "土",
                    java.time.DayOfWeek.SUNDAY to "日"
                )
                val entryMap = entries.associateBy { it.workDate }
                val rows = mutableListOf<List<String>>()
                rows.add(headers)
                var d = from
                while (!d.isAfter(to)) {
                    val e = entryMap[d]
                    val holidayName = e?.note ?: ""
                    val isHolidayWork = e?.holidayWork == true
                    val isActualHoliday = holidayMap.containsKey(d)
                    val isWeekend =
                        d.dayOfWeek == java.time.DayOfWeek.SATURDAY || d.dayOfWeek == java.time.DayOfWeek.SUNDAY
                    val isHoliday = isActualHoliday || isWeekend
                    val shouldBlank = isHoliday && !isHolidayWork
                    rows.add(
                        listOf(
                            holidayName,
                            "${d.dayOfMonth}日",
                            jpWeek[d.dayOfWeek] ?: "",
                            if (shouldBlank) "" else e?.startTime?.toString() ?: "",
                            if (shouldBlank) "" else e?.endTime?.toString() ?: "",
                            if (shouldBlank) "" else e?.breakMinutes?.toString() ?: "",
                            if (shouldBlank) "" else e?.durationMinutes?.let { formatMinutesToHM(it) } ?: "",
                            if (shouldBlank) "" else e?.workingMinutes?.let { formatMinutesToHM(it) } ?: ""
                        ))
                    d = d.plusDays(1)
                }
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    val margin = 40f
                    val yStart = page.mediaBox.height - margin
                    var y = yStart
                    val rowHeight = 20f
                    val fontSize = 10f
                    // タイトル
                    val ymTitle = "${from.year}年${String.format("%02d", from.monthValue)}月度 勤務表"
                    cs.beginText()
                    cs.setFont(font, 14f)
                    val titleWidth = font.getStringWidth(ymTitle) / 1000 * 14f
                    cs.newLineAtOffset(margin + (tableWidth - titleWidth) / 2, y)
                    cs.showText(ymTitle)
                    cs.endText()
                    y -= rowHeight * 1.5f
                    // 会社名・氏名
                    val company = "会社名：ユーニスイースト株式会社"
                    val name = "氏名：$username"
                    cs.beginText()
                    cs.setFont(font, fontSize)
                    cs.newLineAtOffset(margin, y)
                    cs.showText(company)
                    cs.endText()
                    val companyWidth = font.getStringWidth(company) / 1000 * fontSize
                    cs.moveTo(margin, y - 2)
                    cs.lineTo(margin + companyWidth, y - 2)
                    cs.stroke()
                    cs.beginText()
                    val nameWidth = font.getStringWidth(name) / 1000 * fontSize
                    cs.setFont(font, fontSize)
                    cs.newLineAtOffset(margin + tableWidth - nameWidth, y)
                    cs.showText(name)
                    cs.endText()
                    cs.moveTo(margin + tableWidth - nameWidth, y - 2)
                    cs.lineTo(margin + tableWidth, y - 2)
                    cs.stroke()
                    y -= rowHeight * 1.5f
                    // テーブル描画
                    for (rowIdx in rows.indices) {
                        val row = rows[rowIdx]
                        var x = margin
                        // ヘッダー行の背景色（エクセルと同じグレー）
                        val fillColor = when {
                            rowIdx == 0 -> java.awt.Color(217, 217, 217) // ヘッダー：グレー

                            rowIdx > 0 -> {
                                val holiday = row[0].isNotBlank()
                                val youbi = row[2]

                                when {
                                    // 日曜 または 備考欄(祝日名)がある場合 → #FF99CC
                                    holiday || youbi == "日" -> java.awt.Color(0xFF, 0x99, 0xCC)

                                    // 土曜 → #CCCCFF
                                    youbi == "土" -> java.awt.Color(0xCC, 0xCC, 0xFF)

                                    else -> null
                                }
                            }

                            else -> null
                        }

                        for (i in row.indices) {
                            val text = row[i]
                            if (fillColor != null) {
                                cs.setNonStrokingColor(fillColor)
                                cs.addRect(x, y - rowHeight, colWidths[i], rowHeight)
                                cs.fill()
                                cs.setNonStrokingColor(java.awt.Color.BLACK)
                            }
                            cs.addRect(x, y - rowHeight, colWidths[i], rowHeight)
                            cs.stroke()
                            cs.beginText()
                            cs.setFont(font, fontSize)
                            val textWidth = font.getStringWidth(text) / 1000 * fontSize
                            val cellCenter = x + (colWidths[i] / 2)
                            cs.newLineAtOffset(cellCenter - textWidth / 2, y - 15)
                            cs.showText(text)
                            cs.endText()
                            x += colWidths[i]
                        }
                        y -= rowHeight
                    }
                }
                doc.save(baos)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
        return baos.toByteArray()
    }
}
