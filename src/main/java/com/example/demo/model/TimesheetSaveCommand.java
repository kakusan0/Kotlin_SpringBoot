package com.example.demo.model;

import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * saveOrUpdateWithFlags の28パラメータを集約するコマンドDTO。
 */
@Builder
public record TimesheetSaveCommand(
        String userName,
        LocalDate workDate,
        boolean startProvided,
        LocalTime startTime,
        boolean endProvided,
        LocalTime endTime,
        boolean breakProvided,
        Integer breakMinutes,
        boolean force,
        boolean holidayWork,
        boolean noteProvided,
        String note,
        String workLocation,
        String irregularWorkType,
        String irregularWorkDesc,
        String irregularWorkData,
        String lateTime,
        String lateDesc,
        String earlyTime,
        String earlyDesc,
        String freeNote,
        String paidLeave,
        boolean clearIrregular,
        boolean clearLate,
        boolean clearEarly,
        boolean clearFreeNote,
        boolean clearPaidLeave,
        boolean clearWorkLocation
) {
}

