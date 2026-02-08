package com.example.demo.mapper;

import com.example.demo.model.CalendarHoliday;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface CalendarHolidayMapper {
    List<CalendarHoliday> selectByYear(@Param("year") int year);

    List<CalendarHoliday> selectByDateRange(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    CalendarHoliday selectByDate(@Param("holidayDate") LocalDate holidayDate);

    int insert(CalendarHoliday holiday);

    int update(CalendarHoliday holiday);

    int deleteById(@Param("id") Long id);
}
