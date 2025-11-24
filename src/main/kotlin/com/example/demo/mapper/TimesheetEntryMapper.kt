package com.example.demo.mapper

import com.example.demo.model.TimesheetEntry
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import java.time.LocalDate

@Mapper
interface TimesheetEntryMapper {
    fun selectByUserAndDate(
        @Param("userName") userName: String,
        @Param("workDate") workDate: LocalDate
    ): TimesheetEntry?

    fun selectByUserAndRange(
        @Param("userName") userName: String,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate
    ): List<TimesheetEntry>

    fun insert(entry: TimesheetEntry): Int
    fun updateTimes(entry: TimesheetEntry): Int // break/duration/working 追加後も同名で利用
    fun updateTimesForce(entry: TimesheetEntry): Int
    fun updateNote(@Param("id") id: Long, @Param("note") note: String): Int
}
