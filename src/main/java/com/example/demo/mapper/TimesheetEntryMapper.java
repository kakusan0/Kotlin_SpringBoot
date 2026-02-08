package com.example.demo.mapper;

import com.example.demo.model.TimesheetEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface TimesheetEntryMapper {
    TimesheetEntry selectByUserAndDate(
            @Param("userName") String userName,
            @Param("workDate") LocalDate workDate
    );

    List<TimesheetEntry> selectByUserAndRange(
            @Param("userName") String userName,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    int insert(TimesheetEntry entry);

    int updateTimes(TimesheetEntry entry);

    int updateTimesForce(TimesheetEntry entry);

    int updateNote(@Param("id") Long id, @Param("note") String note);
}
