package com.example.demo.controller;

import com.example.demo.dto.AddHolidayRequest;
import com.example.demo.model.CalendarHoliday;
import com.example.demo.service.CalendarHolidayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/calendar")
@Validated
@RequiredArgsConstructor
public class CalendarHolidayController {

    private final CalendarHolidayService calendarHolidayService;


    @GetMapping("/holidays")
    public Map<String, String> getHolidays(@RequestParam int year) {
        return calendarHolidayService.getHolidaysMapByYear(year);
    }

    @GetMapping("/holidays/list")
    public List<CalendarHoliday> getHolidaysList(@RequestParam int year) {
        return calendarHolidayService.getHolidaysByYear(year);
    }

    @GetMapping("/holidays/range")
    public Map<String, String> getHolidaysByRange(
            @RequestParam String from,
            @RequestParam String to
    ) {
        LocalDate fromDate = LocalDate.parse(from);
        LocalDate toDate = LocalDate.parse(to);
        List<CalendarHoliday> holidays = calendarHolidayService.getHolidaysByRange(fromDate, toDate);
        return holidays.stream().collect(Collectors.toMap(
                h -> h.getHolidayDate().toString(),
                CalendarHoliday::getName
        ));
    }

    @PostMapping("/holidays")
    public ResponseEntity<Map<String, Object>> addHoliday(
            @Valid @RequestBody AddHolidayRequest body
    ) {
        CalendarHoliday holiday = calendarHolidayService.addHoliday(body.date(), body.name());
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("holiday", holiday);
        return ResponseEntity.ok(resp);
    }

    @DeleteMapping("/holidays/{id}")
    public ResponseEntity<Map<String, Object>> deleteHoliday(
            @PathVariable Long id
    ) {
        int deleted = calendarHolidayService.deleteHoliday(id);
        if (deleted > 0) {
            return ResponseEntity.ok(Map.of("success", true, "deleted", deleted));
        }
        return ResponseEntity.notFound().build();
    }

}
