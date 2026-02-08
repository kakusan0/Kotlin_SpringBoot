package com.example.demo.service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public final class TimesheetEvaluator {
    private static final int MAX_WORK_DURATION_MIN = 720;
    private static final int MAX_CROSS_DURATION_MIN = 1440;
    private static final double BREAK_RATIO_LIMIT = 0.5;

    private TimesheetEvaluator() {
    }

    public static TimesheetEval evaluate(LocalTime start, LocalTime end, Integer breakMinutes) {
        List<String> errs = new ArrayList<>();
        int breakM = breakMinutes != null ? breakMinutes : 0;
        if (breakM < 0) {
            errs.add("休憩は0以上");
        }
        Integer duration = null;
        Integer working = null;
        if (start != null && end != null) {
            int sTot = start.getHour() * 60 + start.getMinute();
            int eTot = end.getHour() * 60 + end.getMinute();
            duration = (eTot >= sTot) ? (eTot - sTot) : (eTot + 1440 - sTot);
            if (duration <= 0) {
                errs.add("勤務時間が0以下");
            }
            if (duration > MAX_CROSS_DURATION_MIN) {
                errs.add("勤務が24時間を超過");
            }
            if (duration > MAX_WORK_DURATION_MIN) {
                errs.add("最大勤務(12h)超過");
            }
            if (duration > 0) {
                if (breakM > duration * BREAK_RATIO_LIMIT) {
                    errs.add("休憩が勤務の50%を超過");
                }
                if (breakM > duration) {
                    errs.add("休憩が勤務時間を超過");
                }
                if (duration >= 1 && duration <= MAX_CROSS_DURATION_MIN) {
                    working = Math.max(duration - breakM, 0);
                }
            }
        } else {
            if (breakM > 0) {
                errs.add("開始/終了未確定で休憩設定不可");
            }
        }
        return new TimesheetEval(duration, working, errs);
    }
}
