package com.example.demo.service;

import com.example.demo.mapper.CalendarHolidayMapper;
import com.example.demo.model.CalendarHoliday;
import com.example.demo.util.DbUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CalendarHolidayService {

    private final CalendarHolidayMapper calendarHolidayMapper;

    public CalendarHolidayService(CalendarHolidayMapper calendarHolidayMapper) {
        this.calendarHolidayMapper = calendarHolidayMapper;
    }

    @Cacheable(value = "holidays", key = "#year")
    public List<CalendarHoliday> getHolidaysByYear(int year) {
        return DbUtils.dbCall("selectByYear", () -> calendarHolidayMapper.selectByYear(year), year);
    }

    public List<CalendarHoliday> getHolidaysByRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from は to より後ろにできません");
        }
        return DbUtils.dbCall("selectByDateRange", () -> calendarHolidayMapper.selectByDateRange(from, to), from, to);
    }

    @Cacheable(value = "holidaysMap", key = "#year")
    public Map<String, String> getHolidaysMapByYear(int year) {
        List<CalendarHoliday> holidays = getHolidaysByYear(year);
        return holidays.stream().collect(Collectors.toMap(
                h -> h.getHolidayDate().toString(),
                CalendarHoliday::getName
        ));
    }

    public boolean isHoliday(LocalDate date) {
        return DbUtils.dbCall("selectByDate", () -> calendarHolidayMapper.selectByDate(date), date) != null;
    }

    @Transactional
    @CacheEvict(value = {"holidays", "holidaysMap"}, allEntries = true)
    public CalendarHoliday addHoliday(LocalDate date, String name) {
        CalendarHoliday holiday = new CalendarHoliday();
        holiday.setHolidayDate(date);
        holiday.setName(name);
        holiday.setYear(date.getYear());
        DbUtils.dbCall("insert", () -> calendarHolidayMapper.insert(holiday), date, name);
        return holiday;
    }

    @Transactional
    @CacheEvict(value = {"holidays", "holidaysMap"}, allEntries = true)
    public int updateHoliday(Long id, String name) {
        CalendarHoliday holiday = new CalendarHoliday();
        holiday.setId(id);
        holiday.setName(name);
        return DbUtils.dbCall("update", () -> calendarHolidayMapper.update(holiday), id, name);
    }

    @Transactional
    @CacheEvict(value = {"holidays", "holidaysMap"}, allEntries = true)
    public int deleteHoliday(Long id) {
        return DbUtils.dbCall("deleteById", () -> calendarHolidayMapper.deleteById(id), id);
    }
}
