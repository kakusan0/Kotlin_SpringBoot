package com.example.demo.service;

import com.example.demo.model.TimesheetEntry;
import com.example.demo.model.UserSettings;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ReportService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ReportService.class);
    private final TimesheetService timesheetService;
    private final UserSettingsService userSettingsService;
    private final HolidayPosition holidayPosition;
    private final ConcurrentHashMap<Integer, Map<LocalDate, String>> holidayCache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReportService(
            TimesheetService timesheetService,
            UserSettingsService userSettingsService,
            @Value("${report.holidayPosition:MIDDLE}") String holidayPositionStr
    ) {
        this.timesheetService = timesheetService;
        this.userSettingsService = userSettingsService;
        HolidayPosition pos;
        try {
            pos = HolidayPosition.valueOf(holidayPositionStr.toUpperCase());
        } catch (Exception ex) {
            pos = HolidayPosition.MIDDLE;
        }
        this.holidayPosition = pos;
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }

    private static List<String> concat(List<String> a, List<String> b, List<String> c) {
        List<String> out = new ArrayList<>(a.size() + b.size() + c.size());
        out.addAll(a);
        out.addAll(b);
        out.addAll(c);
        return out;
    }

    private static List<String> prefixBullets(List<String> items) {
        List<String> out = new ArrayList<>(items.size());
        for (String item : items) {
            out.add("・" + item);
        }
        return out;
    }

    private static Cell getCell(org.apache.poi.ss.usermodel.Sheet sheet, int rowIdx, int colIdx) {
        var row = sheet.getRow(rowIdx) != null ? sheet.getRow(rowIdx) : sheet.createRow(rowIdx);
        return row.getCell(colIdx) != null ? row.getCell(colIdx) : row.createCell(colIdx);
    }

    public byte[] generateXlsxBytes(String username, LocalDate from, LocalDate to) {
        List<TimesheetEntry> entries = timesheetService.list(username, from, to);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            List<String> baseBefore = List.of("日付", "曜日");
            List<String> baseAfter = List.of("出勤時間", "退勤時間", "休憩", "稼働時間", "実働");
            List<String> headers;
            switch (holidayPosition) {
                case START -> headers = concat(List.of("備考"), baseBefore, baseAfter);
                case END -> headers = concat(baseBefore, baseAfter, List.of("備考"));
                default -> headers = concat(baseBefore, List.of("備考"), baseAfter);
            }

            var sheet = wb.createSheet(username);
            int rowIdx = 0;

            var titleStyle = wb.createCellStyle();
            var titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);
            titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            var leftStyle = wb.createCellStyle();
            leftStyle.setAlignment(HorizontalAlignment.LEFT);
            leftStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            var leftFont = wb.createFont();
            leftFont.setUnderline(Font.U_SINGLE);
            leftStyle.setFont(leftFont);

            var rightStyle = wb.createCellStyle();
            rightStyle.setAlignment(HorizontalAlignment.RIGHT);
            rightStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            var rightFont = wb.createFont();
            rightFont.setUnderline(Font.U_SINGLE);
            rightStyle.setFont(rightFont);

            var headerStyle = wb.createCellStyle();
            var headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderTop(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            headerStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            headerStyle.setBorderLeft(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            headerStyle.setBorderRight(org.apache.poi.ss.usermodel.BorderStyle.THIN);

            String ymTitle = (from.getYear() + "年" + String.format("%02d", from.getMonthValue()) + "月度　勤務表")
                    .replace('　', ' ');
            int cols = headers.size();

            var titleRow = sheet.createRow(rowIdx++);
            var titleCell = titleRow.createCell(0);
            titleCell.setCellValue(ymTitle);
            titleCell.setCellStyle(titleStyle);
            if (cols > 1) {
                sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, cols - 1));
            }

            sheet.createRow(rowIdx++);

            var companyRow = sheet.createRow(rowIdx++);
            var companyCell = companyRow.createCell(0);
            companyCell.setCellValue("会社名：ユーニスイースト株式会社");
            companyCell.setCellStyle(leftStyle);
            var nameCell = companyRow.createCell(Math.max(0, cols - 1));
            nameCell.setCellValue("氏名：" + username);
            nameCell.setCellStyle(rightStyle);

            sheet.createRow(rowIdx++);

            var headerRow = sheet.createRow(rowIdx++);
            for (int i = 0; i < headers.size(); i++) {
                var cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            var df = wb.createDataFormat();
            var baseCellStyle = wb.createCellStyle();
            baseCellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            baseCellStyle.setAlignment(HorizontalAlignment.CENTER);
            baseCellStyle.setBorderTop(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            baseCellStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            baseCellStyle.setBorderLeft(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            baseCellStyle.setBorderRight(org.apache.poi.ss.usermodel.BorderStyle.THIN);

            var dayOnlyStyle = wb.createCellStyle();
            dayOnlyStyle.cloneStyleFrom(baseCellStyle);
            dayOnlyStyle.setAlignment(HorizontalAlignment.CENTER);

            var timeTextStyle = wb.createCellStyle();
            timeTextStyle.cloneStyleFrom(baseCellStyle);
            timeTextStyle.setAlignment(HorizontalAlignment.CENTER);

            var intStyle = wb.createCellStyle();
            intStyle.cloneStyleFrom(baseCellStyle);
            intStyle.setDataFormat(df.getFormat("#,##0"));
            intStyle.setAlignment(HorizontalAlignment.CENTER);

            var defaultTextStyle = wb.createCellStyle();
            defaultTextStyle.cloneStyleFrom(baseCellStyle);
            defaultTextStyle.setAlignment(HorizontalAlignment.CENTER);

            Map<LocalDate, String> holidayMap = fetchHolidayDates(from.getYear(), to.getYear());
            Map<DayOfWeek, String> jpWeek = Map.of(
                    DayOfWeek.MONDAY, "月",
                    DayOfWeek.TUESDAY, "火",
                    DayOfWeek.WEDNESDAY, "水",
                    DayOfWeek.THURSDAY, "木",
                    DayOfWeek.FRIDAY, "金",
                    DayOfWeek.SATURDAY, "土",
                    DayOfWeek.SUNDAY, "日"
            );

            Map<LocalDate, TimesheetEntry> entryMap = new HashMap<>();
            for (TimesheetEntry e : entries) {
                entryMap.put(e.getWorkDate(), e);
            }

            int r = rowIdx;
            LocalDate d = from;
            while (!d.isAfter(to)) {
                var row = sheet.createRow(r++);
                TimesheetEntry e = entryMap.get(d);

                int dateIdx = headers.indexOf("日付");
                int wdIdx = headers.indexOf("曜日");
                int remarkIdx = headers.indexOf("備考");
                int scIdx = headers.indexOf("出勤時間");
                int ecIdx = headers.indexOf("退勤時間");
                int breakIdx = headers.indexOf("休憩");
                int durIdx = headers.indexOf("稼働時間");
                int workIdx = headers.indexOf("実働");

                var dateCell = row.createCell(dateIdx);
                dateCell.setCellValue(d.getDayOfMonth() + "日");
                dateCell.setCellStyle(dayOnlyStyle);

                var wdCell = row.createCell(wdIdx);
                wdCell.setCellValue(jpWeek.get(d.getDayOfWeek()));
                wdCell.setCellStyle(dayOnlyStyle);

                String noteValue = e != null ? safe(e.getNote()) : "";
                String displayNote = "現場休".equals(noteValue) ? "休日" : noteValue;
                var remarkCell = row.createCell(remarkIdx);
                remarkCell.setCellValue(displayNote);
                remarkCell.setCellStyle(defaultTextStyle);

                boolean isActualHoliday = holidayMap.containsKey(d);
                boolean isWeekend = d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
                boolean isHolidayOrWeekend = isActualHoliday || isWeekend;

                List<String> workingNotes = List.of("午前休", "午後休", "休日出勤", "振替出勤", "現場休");
                boolean isWorkingNote = workingNotes.contains(noteValue);
                List<String> blankNotes = List.of("休日", "祝日", "年休", "会社休", "対象外", "振替休日", "特別休暇", "欠勤");
                boolean isBlankNote = blankNotes.contains(noteValue);
                boolean shouldBlank = (isHolidayOrWeekend && !isWorkingNote) || isBlankNote;

                var sc = row.createCell(scIdx);
                sc.setCellValue(shouldBlank ? "" : (e != null && e.getStartTime() != null ? e.getStartTime().toString() : ""));
                sc.setCellStyle(timeTextStyle);

                var ec = row.createCell(ecIdx);
                ec.setCellValue(shouldBlank ? "" : (e != null && e.getEndTime() != null ? e.getEndTime().toString() : ""));
                ec.setCellStyle(timeTextStyle);

                var breakCell = row.createCell(breakIdx);
                if (shouldBlank) {
                    breakCell.setCellValue("");
                } else if (e != null && e.getBreakMinutes() != null) {
                    breakCell.setCellValue(e.getBreakMinutes());
                } else {
                    breakCell.setCellValue("");
                }
                breakCell.setCellStyle(intStyle);

                var durCell = row.createCell(durIdx);
                durCell.setCellValue(shouldBlank ? "" : (e != null && e.getDurationMinutes() != null
                        ? formatMinutesToHM(e.getDurationMinutes()) : ""));
                durCell.setCellStyle(timeTextStyle);

                var workCell = row.createCell(workIdx);
                workCell.setCellValue(shouldBlank ? "" : (e != null && e.getWorkingMinutes() != null
                        ? formatMinutesToHM(e.getWorkingMinutes()) : ""));
                workCell.setCellStyle(timeTextStyle);

                if (isHolidayOrWeekend) {
                    boolean isRed = isActualHoliday || d.getDayOfWeek() == DayOfWeek.SUNDAY;
                    short fillColor = isRed ? IndexedColors.ROSE.getIndex() : IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex();
                    var fontForFill = wb.createFont();
                    fontForFill.setColor(isRed ? IndexedColors.WHITE.getIndex() : IndexedColors.BLACK.getIndex());
                    for (int c = 0; c < headers.size(); c++) {
                        var cell = row.getCell(c) != null ? row.getCell(c) : row.createCell(c);
                        var src = cell.getCellStyle() != null ? cell.getCellStyle() : baseCellStyle;
                        var newStyle = wb.createCellStyle();
                        newStyle.cloneStyleFrom(src);
                        newStyle.setFillForegroundColor(fillColor);
                        newStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                        newStyle.setFont(fontForFill);
                        cell.setCellStyle(newStyle);
                    }
                }

                d = d.plusDays(1);
            }

            for (int i = 0; i < headers.size(); i++) {
                try {
                    sheet.autoSizeColumn(i);
                } catch (Throwable ignored) {
                }
                int current = sheet.getColumnWidth(i);
                int minChars;
                if (i == headers.indexOf("日付")) {
                    minChars = 6;
                } else if (i == headers.indexOf("曜日")) {
                    minChars = 4;
                } else if (i == headers.indexOf("備考")) {
                    minChars = 12;
                } else if (i == headers.indexOf("出勤時間") || i == headers.indexOf("退勤時間")) {
                    minChars = 10;
                } else if (i == headers.indexOf("休憩")) {
                    minChars = 6;
                } else if (i == headers.indexOf("稼働時間") || i == headers.indexOf("実働")) {
                    minChars = 8;
                } else {
                    minChars = 8;
                }
                int min = 256 * minChars;
                if (current < min) {
                    current = min;
                }
                int max = 256 * 40;
                if (current > max) {
                    current = max;
                }
                sheet.setColumnWidth(i, current);
            }

            wb.write(baos);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate XLSX", ex);
        }

        return baos.toByteArray();
    }

    public String formatMinutesToHM(Integer minutes) {
        if (minutes == null) {
            return "";
        }
        int h = minutes / 60;
        int m = minutes % 60;
        return String.format("%d:%02d", h, m);
    }

    private Map<LocalDate, String> fetchHolidayDates(int fromYear, int toYear) {
        Map<LocalDate, String> result = new HashMap<>();
        HttpClient client = HttpClient.newBuilder().build();

        for (int y = fromYear; y <= toYear; y++) {
            Map<LocalDate, String> cached = holidayCache.get(y);
            if (cached != null) {
                result.putAll(cached);
                continue;
            }
            try {
                URI uri = URI.create("https://date.nager.at/api/v3/PublicHolidays/" + y + "/JP");
                HttpRequest req = HttpRequest.newBuilder().uri(uri).GET().build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) {
                    logger.warn("Holiday API returned {} for year {}", resp.statusCode(), y);
                    holidayCache.put(y, Map.of());
                    continue;
                }
                JsonNode root = objectMapper.readTree(resp.body());
                Map<LocalDate, String> mapForYear = new HashMap<>();
                if (root.isArray()) {
                    for (JsonNode node : root) {
                        String dateStr = node.path("date").asText(null);
                        String localName = node.path("localName").asText(null);
                        if (localName == null || localName.isBlank()) {
                            localName = node.path("name").asText("");
                        }
                        if (dateStr != null && !dateStr.isBlank()) {
                            try {
                                LocalDate ld = LocalDate.parse(dateStr);
                                mapForYear.put(ld, localName);
                            } catch (Exception e) {
                                logger.warn("Failed to parse holiday date '{}' for year {}: {}", dateStr, y, e.getMessage());
                            }
                        }
                    }
                }
                holidayCache.put(y, mapForYear);
                result.putAll(mapForYear);
            } catch (Exception e) {
                logger.warn("Failed to fetch holidays for year {}: {}", y, e.getMessage());
                holidayCache.put(y, Map.of());
            }
        }

        return result;
    }

    public byte[] generatePdfBytes(String username, LocalDate from, LocalDate to) {
        List<TimesheetEntry> entries = timesheetService.list(username, from, to);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            InputStream fontStream = getClass().getClassLoader().getResourceAsStream("fonts/KazukiReiwa - Bold.ttf");
            var font = fontStream != null
                    ? PDType0Font.load(doc, fontStream, true)
                    : new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
            List<String> headers = List.of("備考", "日付", "曜日", "出勤時間", "退勤時間", "休憩", "稼働時間", "実働");
            float[] colWidths = new float[]{80f, 50f, 40f, 60f, 60f, 40f, 60f, 60f};
            float tableWidth = 0f;
            for (float w : colWidths) tableWidth += w;

            Map<LocalDate, String> holidayMap = fetchHolidayDates(from.getYear(), to.getYear());
            Map<DayOfWeek, String> jpWeek = Map.of(
                    DayOfWeek.MONDAY, "月",
                    DayOfWeek.TUESDAY, "火",
                    DayOfWeek.WEDNESDAY, "水",
                    DayOfWeek.THURSDAY, "木",
                    DayOfWeek.FRIDAY, "金",
                    DayOfWeek.SATURDAY, "土",
                    DayOfWeek.SUNDAY, "日"
            );

            Map<LocalDate, TimesheetEntry> entryMap = new HashMap<>();
            for (TimesheetEntry e : entries) {
                entryMap.put(e.getWorkDate(), e);
            }

            List<List<String>> rows = new ArrayList<>();
            rows.add(headers);
            LocalDate d = from;
            while (!d.isAfter(to)) {
                TimesheetEntry e = entryMap.get(d);
                String noteValue = e != null ? safe(e.getNote()) : "";
                String displayNote = "現場休".equals(noteValue) ? "休日" : noteValue;

                boolean isActualHoliday = holidayMap.containsKey(d);
                boolean isWeekend = d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
                boolean isHolidayOrWeekend = isActualHoliday || isWeekend;

                List<String> workingNotes = List.of("午前休", "午後休", "休日出勤", "振替出勤", "現場休");
                boolean isWorkingNote = workingNotes.contains(noteValue);
                List<String> blankNotes = List.of("休日", "祝日", "年休", "会社休", "対象外", "振替休日", "特別休暇", "欠勤");
                boolean isBlankNote = blankNotes.contains(noteValue);
                boolean shouldBlank = (isHolidayOrWeekend && !isWorkingNote) || isBlankNote;

                rows.add(List.of(
                        displayNote,
                        d.getDayOfMonth() + "日",
                        jpWeek.getOrDefault(d.getDayOfWeek(), ""),
                        shouldBlank ? "" : (e != null && e.getStartTime() != null ? e.getStartTime().toString() : ""),
                        shouldBlank ? "" : (e != null && e.getEndTime() != null ? e.getEndTime().toString() : ""),
                        shouldBlank ? "" : (e != null && e.getBreakMinutes() != null ? e.getBreakMinutes().toString() : ""),
                        shouldBlank ? "" : (e != null && e.getDurationMinutes() != null ? formatMinutesToHM(e.getDurationMinutes()) : ""),
                        shouldBlank ? "" : (e != null && e.getWorkingMinutes() != null ? formatMinutesToHM(e.getWorkingMinutes()) : "")
                ));
                d = d.plusDays(1);
            }

            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float margin = 40f;
                float yStart = page.getMediaBox().getHeight() - margin;
                float y = yStart;
                float rowHeight = 20f;
                float fontSize = 10f;

                String ymTitle = from.getYear() + "年" + String.format("%02d", from.getMonthValue()) + "月度 勤務表";
                cs.beginText();
                cs.setFont(font, 14f);
                float titleWidth = font.getStringWidth(ymTitle) / 1000 * 14f;
                cs.newLineAtOffset(margin + (tableWidth - titleWidth) / 2, y);
                cs.showText(ymTitle);
                cs.endText();

                y -= rowHeight * 1.5f;

                String company = "会社名：ユーニスイースト株式会社";
                String name = "氏名：" + username;

                cs.beginText();
                cs.setFont(font, fontSize);
                cs.newLineAtOffset(margin, y);
                cs.showText(company);
                cs.endText();

                float companyWidth = font.getStringWidth(company) / 1000 * fontSize;
                cs.moveTo(margin, y - 2);
                cs.lineTo(margin + companyWidth, y - 2);
                cs.stroke();

                float nameWidth = font.getStringWidth(name) / 1000 * fontSize;
                cs.beginText();
                cs.setFont(font, fontSize);
                cs.newLineAtOffset(margin + tableWidth - nameWidth, y);
                cs.showText(name);
                cs.endText();

                cs.moveTo(margin + tableWidth - nameWidth, y - 2);
                cs.lineTo(margin + tableWidth, y - 2);
                cs.stroke();

                y -= rowHeight * 1.5f;

                for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
                    List<String> row = rows.get(rowIdx);
                    float x = margin;

                    Color fillColor = null;
                    if (rowIdx == 0) {
                        fillColor = new Color(217, 217, 217);
                    } else {
                        int dateIdx = 1;
                        int weekdayIdx = 2;
                        String dayStr = row.get(dateIdx).replace("日", "");
                        int dayOfMonth = 1;
                        try {
                            dayOfMonth = Integer.parseInt(dayStr);
                        } catch (NumberFormatException ignored) {
                        }
                        LocalDate currentDate = from;
                        while (currentDate.getDayOfMonth() != dayOfMonth && !currentDate.isAfter(to)) {
                            currentDate = currentDate.plusDays(1);
                        }
                        String youbi = row.get(weekdayIdx);
                        boolean isActualHoliday = holidayMap.containsKey(currentDate);
                        if ("日".equals(youbi) || isActualHoliday) {
                            fillColor = new Color(0xFF, 0x99, 0xCC);
                        } else if ("土".equals(youbi)) {
                            fillColor = new Color(0xCC, 0xCC, 0xFF);
                        }
                    }

                    for (int i = 0; i < row.size(); i++) {
                        String text = row.get(i);
                        if (fillColor != null) {
                            cs.setNonStrokingColor(fillColor);
                            cs.addRect(x, y - rowHeight, colWidths[i], rowHeight);
                            cs.fill();
                            cs.setNonStrokingColor(Color.BLACK);
                        }
                        cs.addRect(x, y - rowHeight, colWidths[i], rowHeight);
                        cs.stroke();
                        cs.beginText();
                        cs.setFont(font, fontSize);
                        float textWidth = font.getStringWidth(text) / 1000 * fontSize;
                        float cellCenter = x + (colWidths[i] / 2);
                        cs.newLineAtOffset(cellCenter - textWidth / 2, y - 15);
                        cs.showText(text);
                        cs.endText();
                        x += colWidths[i];
                    }
                    y -= rowHeight;
                }
            }
            doc.save(baos);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate PDF", e);
        }
        return baos.toByteArray();
    }

    public byte[] generateUnissXlsxBytes(String username, LocalDate from, LocalDate to) {
        List<TimesheetEntry> entries = timesheetService.list(username, from, to);
        Map<LocalDate, TimesheetEntry> entryMap = new HashMap<>();
        for (TimesheetEntry e : entries) {
            entryMap.put(e.getWorkDate(), e);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String templatePath = "2025年10月度UNISS勤務表(〇〇).xlsx";
        ClassPathResource templateResource = new ClassPathResource(templatePath);

        try (InputStream templateStream = templateResource.getInputStream();
             XSSFWorkbook wb = new XSSFWorkbook(templateStream)) {
            var sheet = wb.getSheetAt(0);

            Map<LocalDate, String> holidayMap = fetchHolidayDates(from.getYear(), to.getYear());

            var monthRow = sheet.getRow(1) != null ? sheet.getRow(1) : sheet.createRow(1);
            var monthCell = monthRow.getCell(14) != null ? monthRow.getCell(14) : monthRow.createCell(14);
            monthCell.setCellValue(from.getMonthValue());

            var yearCell = monthRow.getCell(12) != null ? monthRow.getCell(12) : monthRow.createCell(12);
            yearCell.setCellValue(from.getYear());

            UserSettings settings = userSettingsService.getSettings(username);

            if (settings != null) {
                if (settings.getCompanyAffiliation() != null)
                    getCell(sheet, 1, 3).setCellValue(settings.getCompanyAffiliation());
                if (settings.getBranchOffice() != null) getCell(sheet, 1, 8).setCellValue(settings.getBranchOffice());
                if (settings.getSection() != null)
                    getCell(sheet, 2, 6).setCellValue(settings.getSection().doubleValue());
                if (settings.getWorkGroup() != null)
                    getCell(sheet, 2, 10).setCellValue(settings.getWorkGroup().doubleValue());
                if (settings.getEmployeeNumber() != null)
                    getCell(sheet, 3, 3).setCellValue(settings.getEmployeeNumber());
                if (settings.getSiteRegularHours() != null) {
                    double fraction = settings.getSiteRegularHours().toSecondOfDay() / 86400.0;
                    getCell(sheet, 3, 9).setCellValue(fraction);
                }
                getCell(sheet, 4, 3).setCellValue(settings.getDisplayName() != null ? settings.getDisplayName() : username);
            } else {
                getCell(sheet, 4, 3).setCellValue(username);
            }

            int colStartHour = 4;
            int colStartMin = 5;
            int colEndHour = 6;
            int colEndMin = 7;
            int colBreak = 8;
            int colHalfDay = 10;
            int colLate = 11;
            int colEarly = 12;
            int colOffice = 13;
            int colRemote = 14;
            int colAnnualLeave = 15;
            int colSpecialLeave = 16;
            int colAbsence = 17;
            int colSubstituteHoliday = 18;
            int colSubstituteWork = 19;
            int colHolidayWork = 20;
            int colDescription = 27;

            int dataStartRow = 9;
            int daysInMonth = from.lengthOfMonth();

            for (int day = 1; day <= daysInMonth; day++) {
                int rowIdx = dataStartRow + (day - 1);
                LocalDate date = from.withDayOfMonth(day);
                TimesheetEntry entry = entryMap.get(date);
                var row = sheet.getRow(rowIdx) != null ? sheet.getRow(rowIdx) : sheet.createRow(rowIdx);

                boolean isActualHoliday = holidayMap.containsKey(date);
                boolean isWeekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
                boolean isHolidayOrWeekend = isActualHoliday || isWeekend;

                String noteValue = entry != null ? safe(entry.getNote()) : "";

                List<IrregularItem> irregularItems = parseIrregularWorkData(
                        entry != null ? entry.getIrregularWorkData() : null,
                        entry != null ? entry.getIrregularWorkType() : null,
                        entry != null ? entry.getIrregularWorkDesc() : null
                );

                boolean hasWorkingIrregular = irregularItems.stream().anyMatch(it ->
                        "振替出勤".equals(it.type) || "休日出勤".equals(it.type)
                );

                boolean isWorkingDay = (!isWeekend && !"祝日".equals(noteValue) && !"休日".equals(noteValue)) || hasWorkingIrregular;
                var workingDayCell = row.getCell(1) != null ? row.getCell(1) : row.createCell(1);
                if (workingDayCell.getCellType() == CellType.FORMULA) {
                    workingDayCell.setCellFormula(null);
                }
                workingDayCell.setCellValue(isWorkingDay ? 1.0 : 0.0);

                List<String> workingNotes = List.of("午前休", "午後休", "休日出勤", "振替出勤");
                boolean isWorkingNote = workingNotes.contains(noteValue);
                List<String> blankNotes = List.of("休日", "祝日", "年休", "会社休", "対象外", "振替休日", "特別休暇", "欠勤");
                boolean isBlankNote = blankNotes.contains(noteValue);
                boolean shouldBlankTime = (isHolidayOrWeekend && !isWorkingNote) || isBlankNote;

                var startHourCell = row.getCell(colStartHour) != null ? row.getCell(colStartHour) : row.createCell(colStartHour);
                var startMinCell = row.getCell(colStartMin) != null ? row.getCell(colStartMin) : row.createCell(colStartMin);
                if (!shouldBlankTime && entry != null && entry.getStartTime() != null) {
                    startHourCell.setCellValue(entry.getStartTime().getHour());
                    startMinCell.setCellValue(entry.getStartTime().getMinute());
                } else {
                    startHourCell.setBlank();
                    startMinCell.setBlank();
                }

                var endHourCell = row.getCell(colEndHour) != null ? row.getCell(colEndHour) : row.createCell(colEndHour);
                var endMinCell = row.getCell(colEndMin) != null ? row.getCell(colEndMin) : row.createCell(colEndMin);
                if (!shouldBlankTime && entry != null && entry.getEndTime() != null) {
                    endHourCell.setCellValue(entry.getEndTime().getHour());
                    endMinCell.setCellValue(entry.getEndTime().getMinute());
                } else {
                    endHourCell.setBlank();
                    endMinCell.setBlank();
                }

                var breakCell = row.getCell(colBreak) != null ? row.getCell(colBreak) : row.createCell(colBreak);
                if (!shouldBlankTime && entry != null && entry.getBreakMinutes() != null) {
                    breakCell.setCellValue(entry.getBreakMinutes());
                } else {
                    breakCell.setBlank();
                }

                var halfDayCell = row.getCell(colHalfDay) != null ? row.getCell(colHalfDay) : row.createCell(colHalfDay);
                if (!isHolidayOrWeekend && ("午前休".equals(noteValue) || "午後休".equals(noteValue))) {
                    halfDayCell.setCellValue("4:00");
                } else {
                    halfDayCell.setBlank();
                }

                var officeCell = row.getCell(colOffice) != null ? row.getCell(colOffice) : row.createCell(colOffice);
                var remoteCell = row.getCell(colRemote) != null ? row.getCell(colRemote) : row.createCell(colRemote);
                String workLocation = entry != null ? safe(entry.getWorkLocation()) : "";
                boolean shouldBlankLocation = isBlankNote || (isHolidayOrWeekend && !isWorkingNote);

                if (shouldBlankLocation) {
                    officeCell.setBlank();
                    remoteCell.setBlank();
                } else if ("出社".equals(workLocation)) {
                    officeCell.setCellValue("○");
                    remoteCell.setBlank();
                } else if ("在宅".equals(workLocation)) {
                    officeCell.setBlank();
                    remoteCell.setCellValue("○");
                } else {
                    officeCell.setBlank();
                    remoteCell.setBlank();
                }

                var lateCell = row.getCell(colLate) != null ? row.getCell(colLate) : row.createCell(colLate);
                String lateTime = entry != null ? entry.getLateTime() : null;
                if (lateTime != null && !lateTime.isBlank()) {
                    lateCell.setCellValue(lateTime);
                } else {
                    lateCell.setBlank();
                }

                var earlyCell = row.getCell(colEarly) != null ? row.getCell(colEarly) : row.createCell(colEarly);
                String earlyTime = entry != null ? entry.getEarlyTime() : null;
                if (earlyTime != null && !earlyTime.isBlank()) {
                    earlyCell.setCellValue(earlyTime);
                } else {
                    earlyCell.setBlank();
                }

                var descCell = row.getCell(colDescription) != null ? row.getCell(colDescription) : row.createCell(colDescription);
                List<String> descriptions = new ArrayList<>();
                if (entry != null && entry.getLateDesc() != null && !entry.getLateDesc().isBlank()) {
                    descriptions.add("遅刻: " + entry.getLateDesc());
                }
                if (entry != null && entry.getEarlyDesc() != null && !entry.getEarlyDesc().isBlank()) {
                    descriptions.add("早退: " + entry.getEarlyDesc());
                }
                for (IrregularItem item : irregularItems) {
                    if (!item.desc.isBlank()) {
                        descriptions.add(item.type + ": " + item.desc);
                    } else {
                        descriptions.add(item.type);
                    }
                }

                String freeNoteValue = entry != null ? entry.getFreeNote() : null;
                if (freeNoteValue != null && !freeNoteValue.isBlank()) {
                    if (!descriptions.isEmpty()) {
                        descCell.setCellValue(String.join("\n", prefixBullets(descriptions)) + "\n" + freeNoteValue);
                    } else {
                        descCell.setCellValue(freeNoteValue);
                    }
                    var style = wb.createCellStyle();
                    style.setWrapText(true);
                    descCell.setCellStyle(style);
                } else if (!descriptions.isEmpty()) {
                    descCell.setCellValue(String.join("\n", prefixBullets(descriptions)));
                    var style = wb.createCellStyle();
                    style.setWrapText(true);
                    descCell.setCellStyle(style);
                } else {
                    descCell.setBlank();
                }

                var annualLeaveCell = row.getCell(colAnnualLeave) != null ? row.getCell(colAnnualLeave) : row.createCell(colAnnualLeave);
                var specialLeaveCell = row.getCell(colSpecialLeave) != null ? row.getCell(colSpecialLeave) : row.createCell(colSpecialLeave);
                var absenceCell = row.getCell(colAbsence) != null ? row.getCell(colAbsence) : row.createCell(colAbsence);
                var substituteHolidayCell = row.getCell(colSubstituteHoliday) != null ? row.getCell(colSubstituteHoliday) : row.createCell(colSubstituteHoliday);
                var substituteWorkCell = row.getCell(colSubstituteWork) != null ? row.getCell(colSubstituteWork) : row.createCell(colSubstituteWork);
                var holidayWorkCell = row.getCell(colHolidayWork) != null ? row.getCell(colHolidayWork) : row.createCell(colHolidayWork);

                annualLeaveCell.setBlank();
                specialLeaveCell.setBlank();
                absenceCell.setBlank();
                substituteHolidayCell.setBlank();
                substituteWorkCell.setBlank();
                holidayWorkCell.setBlank();

                List<String> annualLeaveNotes = List.of("午前休", "午後休", "年休");
                if (annualLeaveNotes.contains(noteValue)) {
                    annualLeaveCell.setCellValue("○");
                }

                for (IrregularItem item : irregularItems) {
                    switch (item.type) {
                        case "有給休暇" -> annualLeaveCell.setCellValue("○");
                        case "特別休暇" -> specialLeaveCell.setCellValue("○");
                        case "欠勤" -> absenceCell.setCellValue("○");
                        case "振替休日" -> substituteHolidayCell.setCellValue("○");
                        case "振替出勤" -> substituteWorkCell.setCellValue("○");
                        case "休日出勤" -> holidayWorkCell.setCellValue("○");
                        default -> {
                        }
                    }
                }

                if (isHolidayOrWeekend && ("午前休".equals(noteValue) || "午後休".equals(noteValue))) {
                    holidayWorkCell.setCellValue("○");
                }
            }

            int colHoliday = 43;
            int holidayStartRow = 9;

            int fiscalYear = (from.getMonthValue() >= 4) ? from.getYear() : from.getYear() - 1;
            logger.info("[UNISS] Fiscal year calculated: {} (from month: {})", fiscalYear, from.getMonthValue());

            Map<LocalDate, String> fiscalYearHolidayMap = fetchHolidayDates(fiscalYear, fiscalYear + 1);
            logger.info("[UNISS] Fetched holidays for fiscal year {}: {} holidays", fiscalYear, fiscalYearHolidayMap.size());

            LocalDate displayYearStart = LocalDate.of(from.getYear(), 1, 1);
            LocalDate displayYearEnd = LocalDate.of(from.getYear(), 12, 31);
            List<LocalDate> displayYearHolidays = new ArrayList<>();
            for (LocalDate date : fiscalYearHolidayMap.keySet()) {
                if (!date.isBefore(displayYearStart) && !date.isAfter(displayYearEnd)) {
                    displayYearHolidays.add(date);
                }
            }
            displayYearHolidays.sort(LocalDate::compareTo);
            logger.info("[UNISS] Filtered holidays in display year range: {} holidays", displayYearHolidays.size());

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
            for (int i = 0; i < displayYearHolidays.size(); i++) {
                LocalDate holidayDate = displayYearHolidays.get(i);
                int rowIdx = holidayStartRow + i;
                var row = sheet.getRow(rowIdx) != null ? sheet.getRow(rowIdx) : sheet.createRow(rowIdx);
                var holidayCell = row.getCell(colHoliday) != null ? row.getCell(colHoliday) : row.createCell(colHoliday);
                if (holidayCell.getCellType() == CellType.FORMULA) {
                    holidayCell.setCellFormula(null);
                }
                String formattedDate = holidayDate.format(formatter);
                holidayCell.setCellValue(formattedDate);
                logger.debug("[UNISS] Set holiday at AR{}: {} ({})", rowIdx + 1, formattedDate, fiscalYearHolidayMap.get(holidayDate));
            }

            wb.setForceFormulaRecalculation(true);
            wb.write(baos);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate UNISS XLSX", ex);
        }

        return baos.toByteArray();
    }

    private List<IrregularItem> parseIrregularWorkData(String irregularWorkData, String irregularWorkType, String irregularWorkDesc) {
        if (irregularWorkData != null && !irregularWorkData.isBlank()) {
            try {
                List<Map<String, String>> items = objectMapper.readValue(
                        irregularWorkData,
                        new TypeReference<List<Map<String, String>>>() {
                        }
                );
                List<IrregularItem> out = new ArrayList<>();
                for (Map<String, String> item : items) {
                    String type = item.get("type");
                    if (type == null) {
                        continue;
                    }
                    String desc = item.getOrDefault("desc", "");
                    out.add(new IrregularItem(type, desc));
                }
                return out;
            } catch (Exception e) {
                logger.warn("[UNISS] irregularWorkData parse error: {} irregularWorkData=[{}]", e.getMessage(), irregularWorkData);
                return List.of();
            }
        }

        if (irregularWorkType != null && !irregularWorkType.isBlank()) {
            return List.of(new IrregularItem(irregularWorkType, irregularWorkDesc != null ? irregularWorkDesc : ""));
        }

        return List.of();
    }

    private enum HolidayPosition {START, MIDDLE, END}

    private record IrregularItem(String type, String desc) {
    }
}
