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
import org.springframework.core.io.ClassPathResource
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
                HolidayPosition.START -> listOf("備考") + baseBefore + baseAfter
                HolidayPosition.END -> baseBefore + baseAfter + listOf("備考")
                else -> baseBefore + listOf("備考") + baseAfter
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
                val remarkIdx = headers.indexOf("備考")
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

                // 備考情報
                val noteValue = e?.note ?: ""
                val remarkCell = row.createCell(remarkIdx)
                remarkCell.setCellValue(noteValue)
                remarkCell.cellStyle = defaultTextStyle

                // 判定: その日が祝日かどうか、週末かどうかを分けて計算
                val isActualHoliday = holidayMap.containsKey(d)
                val isWeekend =
                    (d.dayOfWeek == java.time.DayOfWeek.SATURDAY || d.dayOfWeek == java.time.DayOfWeek.SUNDAY)
                val isHolidayOrWeekend = isActualHoliday || isWeekend

                // 勤務時間を出力するべき備考（土日祝でも出力する）
                val workingNotes = listOf("午前休", "午後休", "休日出勤", "振替出勤")
                val isWorkingNote = workingNotes.contains(noteValue)

                // 時間を空欄にすべき備考（完全な休み）
                val blankNotes = listOf("休日", "祝日", "年休", "会社休", "対象外", "振替休日", "特別休暇", "欠勤")
                val isBlankNote = blankNotes.contains(noteValue)

                // 土日祝で勤務系の備考がない場合、または完全休みの備考の場合は時間を空欄
                val shouldBlank = (isHolidayOrWeekend && !isWorkingNote) || isBlankNote

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
                if (isHolidayOrWeekend) {
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
                    headers.indexOf("備考") -> 12
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
                    val noteValue = e?.note ?: ""
                    val isActualHoliday = holidayMap.containsKey(d)
                    val isWeekend =
                        d.dayOfWeek == java.time.DayOfWeek.SATURDAY || d.dayOfWeek == java.time.DayOfWeek.SUNDAY
                    val isHolidayOrWeekend = isActualHoliday || isWeekend

                    // 勤務時間を出力するべき備考（土日祝でも出力する）
                    val workingNotes = listOf("午前休", "午後休", "休日出勤", "振替出勤")
                    val isWorkingNote = workingNotes.contains(noteValue)

                    // 時間を空欄にすべき備考（完全な休み）
                    val blankNotes = listOf("休日", "祝日", "年休", "会社休", "対象外", "振替休日", "特別休暇", "欠勤")
                    val isBlankNote = blankNotes.contains(noteValue)

                    // 土日祝で勤務系の備考がない場合、または完全休みの備考の場合は時間を空欄
                    val shouldBlank = (isHolidayOrWeekend && !isWorkingNote) || isBlankNote
                    rows.add(
                        listOf(
                            noteValue,
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
                                val dateIdx = 1 // "日付"列
                                val weekdayIdx = 2 // "曜日"列

                                // rowsからdateを復元
                                val dayStr = row[dateIdx].removeSuffix("日")
                                val dayOfMonth = dayStr.toIntOrNull() ?: 1
                                var currentDate = from
                                while (currentDate.dayOfMonth != dayOfMonth && !currentDate.isAfter(to)) {
                                    currentDate = currentDate.plusDays(1)
                                }

                                val youbi = row[weekdayIdx]
                                val isActualHoliday = holidayMap.containsKey(currentDate)

                                when {
                                    // 日曜 または 祝日 → #FF99CC
                                    youbi == "日" || isActualHoliday -> java.awt.Color(0xFF, 0x99, 0xCC)

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

    /**
     * UNISS勤務表テンプレートを使用してエクセルファイルを生成する
     * テンプレート: 2025年10月度UNISS勤務表(角谷亮洋).xlsx
     */
    fun generateUnissXlsxBytes(username: String, from: LocalDate, to: LocalDate): ByteArray {
        val entries = timesheetService.list(username, from, to)
        val entryMap = entries.associateBy { it.workDate }
        val baos = ByteArrayOutputStream()

        // テンプレートファイルを読み込む
        val templatePath = "2025年10月度UNISS勤務表(〇〇).xlsx"
        val templateResource = ClassPathResource(templatePath)

        templateResource.inputStream.use { templateStream ->
            XSSFWorkbook(templateStream).use { wb ->
                val sheet = wb.getSheetAt(0) // 最初のシートを使用

                // 祝日マップを取得
                val holidayMap = fetchHolidayDates(from.year, to.year)

                // O2セルに月を入力 (O列 = インデックス14, 行2 = インデックス1)
                val monthRow = sheet.getRow(1) ?: sheet.createRow(1)
                val monthCell = monthRow.getCell(14) ?: monthRow.createCell(14)
                monthCell.setCellValue(from.monthValue.toDouble())

                // 固定の列インデックス（0-indexed）
                // E列=4: 出勤時、F列=5: 出勤分、G列=6: 退勤時、H列=7: 退勤分、I列=8: 休憩
                val colStartHour = 4   // E列: 出勤時
                val colStartMin = 5    // F列: 出勤分
                val colEndHour = 6     // G列: 退勤時
                val colEndMin = 7      // H列: 退勤分
                val colBreak = 8       // I列: 休憩
                val colHalfDay = 10    // K列: 午前休/午後休の場合に「4:00」
                val colOffice = 13     // N列: 出社
                val colRemote = 14     // O列: 在宅
                val colAnnualLeave = 15 // P列: 年休
                val colSpecialLeave = 16 // Q列: 特別休暇
                val colAbsence = 17    // R列: 欠勤
                val colSubstituteHoliday = 18 // S列: 振替休日
                val colSubstituteWork = 19 // T列: 振替出勤
                val colHolidayWork = 20 // U列: 休日出勤

                // 10行目（インデックス9）からデータ入力開始
                val dataStartRow = 9
                val daysInMonth = from.lengthOfMonth()

                for (day in 1..daysInMonth) {
                    val rowIdx = dataStartRow + (day - 1)
                    val date = from.withDayOfMonth(day)
                    val entry = entryMap[date]
                    val row = sheet.getRow(rowIdx) ?: sheet.createRow(rowIdx)

                    // 祝日・週末判定
                    val isActualHoliday = holidayMap.containsKey(date)
                    val isWeekend = date.dayOfWeek == java.time.DayOfWeek.SATURDAY ||
                            date.dayOfWeek == java.time.DayOfWeek.SUNDAY
                    val isHolidayOrWeekend = isActualHoliday || isWeekend

                    // 備考値
                    val noteValue = entry?.note ?: ""

                    // 勤務時間を出力するべき備考（土日祝でも出力する）
                    val workingNotes = listOf("午前休", "午後休", "休日出勤", "振替出勤")
                    val isWorkingNote = workingNotes.contains(noteValue)

                    // 時間を空欄にすべき備考（完全な休み）
                    val blankNotes = listOf("休日", "祝日", "年休", "会社休", "対象外", "振替休日", "特別休暇", "欠勤")
                    val isBlankNote = blankNotes.contains(noteValue)

                    // 土日祝で勤務系の備考がない場合、または完全休みの備考の場合は時間を空欄
                    val shouldBlankTime = (isHolidayOrWeekend && !isWorkingNote) || isBlankNote

                    // 出勤時間を入力（時と分を別々のセルに）
                    val startHourCell = row.getCell(colStartHour) ?: row.createCell(colStartHour)
                    val startMinCell = row.getCell(colStartMin) ?: row.createCell(colStartMin)
                    if (!shouldBlankTime && entry?.startTime != null) {
                        startHourCell.setCellValue(entry.startTime.hour.toDouble())
                        startMinCell.setCellValue(entry.startTime.minute.toDouble())
                    } else {
                        startHourCell.setBlank()
                        startMinCell.setBlank()
                    }

                    // 退勤時間を入力（時と分を別々のセルに）
                    val endHourCell = row.getCell(colEndHour) ?: row.createCell(colEndHour)
                    val endMinCell = row.getCell(colEndMin) ?: row.createCell(colEndMin)
                    if (!shouldBlankTime && entry?.endTime != null) {
                        endHourCell.setCellValue(entry.endTime.hour.toDouble())
                        endMinCell.setCellValue(entry.endTime.minute.toDouble())
                    } else {
                        endHourCell.setBlank()
                        endMinCell.setBlank()
                    }

                    // 休憩時間を入力
                    val breakCell = row.getCell(colBreak) ?: row.createCell(colBreak)
                    if (!shouldBlankTime && entry?.breakMinutes != null) {
                        breakCell.setCellValue(entry.breakMinutes.toDouble())
                    } else {
                        breakCell.setBlank()
                    }

                    // K列: 午前休または午後休の場合に「4:00」を入力（土日祝以外のみ）
                    val halfDayCell = row.getCell(colHalfDay) ?: row.createCell(colHalfDay)
                    if (!isHolidayOrWeekend && (noteValue == "午前休" || noteValue == "午後休")) {
                        halfDayCell.setCellValue("4:00")
                    } else {
                        halfDayCell.setBlank()
                    }

                    // 出社区分: N列(出社)、O列(在宅)に〇
                    // 休日系や土日祝で勤務しない場合は空欄
                    val officeCell = row.getCell(colOffice) ?: row.createCell(colOffice)
                    val remoteCell = row.getCell(colRemote) ?: row.createCell(colRemote)
                    val workLocation = entry?.workLocation ?: ""
                    val shouldBlankLocation = isBlankNote || (isHolidayOrWeekend && !isWorkingNote)

                    if (shouldBlankLocation) {
                        officeCell.setBlank()
                        remoteCell.setBlank()
                    } else if (workLocation == "出社") {
                        officeCell.setCellValue("〇")
                        remoteCell.setBlank()
                    } else if (workLocation == "在宅") {
                        officeCell.setBlank()
                        remoteCell.setCellValue("〇")
                    } else {
                        officeCell.setBlank()
                        remoteCell.setBlank()
                    }

                    // 備考による各列への〇入力
                    val annualLeaveCell = row.getCell(colAnnualLeave) ?: row.createCell(colAnnualLeave)
                    val specialLeaveCell = row.getCell(colSpecialLeave) ?: row.createCell(colSpecialLeave)
                    val absenceCell = row.getCell(colAbsence) ?: row.createCell(colAbsence)
                    val substituteHolidayCell =
                        row.getCell(colSubstituteHoliday) ?: row.createCell(colSubstituteHoliday)
                    val substituteWorkCell = row.getCell(colSubstituteWork) ?: row.createCell(colSubstituteWork)
                    val holidayWorkCell = row.getCell(colHolidayWork) ?: row.createCell(colHolidayWork)

                    // すべてクリア
                    annualLeaveCell.setBlank()
                    specialLeaveCell.setBlank()
                    absenceCell.setBlank()
                    substituteHolidayCell.setBlank()
                    substituteWorkCell.setBlank()
                    holidayWorkCell.setBlank()

                    // 備考に応じて〇を入力
                    when (noteValue) {
                        "年休" -> annualLeaveCell.setCellValue("〇")
                        "午前休", "午後休" -> {
                            // 土日祝の場合は休日出勤のみ、平日は有給休暇
                            if (isHolidayOrWeekend) {
                                holidayWorkCell.setCellValue("〇")
                            } else {
                                annualLeaveCell.setCellValue("〇")
                            }
                        }

                        "特別休暇" -> specialLeaveCell.setCellValue("〇")
                        "欠勤" -> absenceCell.setCellValue("〇")
                        "振替休日" -> substituteHolidayCell.setCellValue("〇")
                        "振替出勤" -> substituteWorkCell.setCellValue("〇")
                        "休日出勤" -> holidayWorkCell.setCellValue("〇")
                    }
                }

                // 数式を再計算
                wb.forceFormulaRecalculation = true

                wb.write(baos)
            }
        }
        return baos.toByteArray()
    }
}
