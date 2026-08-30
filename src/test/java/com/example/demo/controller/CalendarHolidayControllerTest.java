package com.example.demo.controller;

import com.example.demo.config.GlobalExceptionHandler;
import com.example.demo.model.CalendarHoliday;
import com.example.demo.service.CalendarHolidayService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = CalendarHolidayControllerTest.TestApplication.class,
        properties = "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class CalendarHolidayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final CalendarHolidayService calendarHolidayService;

    CalendarHolidayControllerTest(@Autowired CalendarHolidayService calendarHolidayService) {
        this.calendarHolidayService = calendarHolidayService;
    }

    @Test
    void getHolidaysReturnsMap() throws Exception {
        when(calendarHolidayService.getHolidaysMapByYear(2026))
                .thenReturn(Map.of("2026-01-01", "元日"));

        mockMvc.perform(get("/api/calendar/holidays").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.2026-01-01").value("元日"));
    }

    @Test
    void addHolidayReturnsCreatedHoliday() throws Exception {
        CalendarHoliday holiday = new CalendarHoliday();
        holiday.setId(1L);
        holiday.setHolidayDate(LocalDate.of(2026, 8, 30));
        holiday.setName("臨時休業");
        when(calendarHolidayService.addHoliday(LocalDate.of(2026, 8, 30), "臨時休業"))
                .thenReturn(holiday);

        mockMvc.perform(post("/api/calendar/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-08-30\",\"name\":\"臨時休業\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.holiday.name").value("臨時休業"));
    }

    @Test
    void invalidDateRangeIsHandledByAdvice() throws Exception {
        doThrow(new IllegalArgumentException("from は to より後ろにできません"))
                .when(calendarHolidayService)
                .getHolidaysByRange(any(), any());

        mockMvc.perform(get("/api/calendar/holidays/range")
                        .param("from", "2026-08-31")
                        .param("to", "2026-08-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("不正な入力値が検出されました"));
    }

    @Test
    void deleteHolidayReturnsNotFoundWhenNothingWasDeleted() throws Exception {
        when(calendarHolidayService.deleteHoliday(99L)).thenReturn(0);

        mockMvc.perform(delete("/api/calendar/holidays/99"))
                .andExpect(status().isNotFound());

        verify(calendarHolidayService).deleteHoliday(eq(99L));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({CalendarHolidayController.class, GlobalExceptionHandler.class, MockBeans.class})
    static class TestApplication {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MockBeans {
        @Bean
        CalendarHolidayService calendarHolidayService() {
            return org.mockito.Mockito.mock(CalendarHolidayService.class);
        }
    }
}
