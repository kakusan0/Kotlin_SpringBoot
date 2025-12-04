package com.example.demo.mapper

import com.example.demo.model.CalendarHoliday
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import java.time.LocalDate

@Mapper
interface CalendarHolidayMapper {
    fun selectByYear(@Param("year") year: Int): List<CalendarHoliday>

    fun selectByDateRange(
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate
    ): List<CalendarHoliday>

    fun selectByDate(@Param("holidayDate") holidayDate: LocalDate): CalendarHoliday?

    fun insert(holiday: CalendarHoliday): Int

    fun update(holiday: CalendarHoliday): Int

    fun deleteById(@Param("id") id: Long): Int
}

