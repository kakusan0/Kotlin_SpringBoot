package com.example.demo.util;

import lombok.experimental.UtilityClass;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@UtilityClass
public class TimesheetGenerator {

    public List<LocalDate> generateDates(YearMonth yearMonth) {
        int days = yearMonth.lengthOfMonth();
        List<LocalDate> dates = new ArrayList<>(days);
        for (int day = 1; day <= days; day++) {
            dates.add(yearMonth.atDay(day));
        }
        return dates;
    }

    public String formatYearMonth(YearMonth yearMonth) {
        return formatYearMonth(yearMonth, Locale.JAPAN);
    }

    public String formatYearMonth(YearMonth yearMonth, Locale locale) {
        return String.format(locale, "%d年%d月", yearMonth.getYear(), yearMonth.getMonthValue());
    }
}
