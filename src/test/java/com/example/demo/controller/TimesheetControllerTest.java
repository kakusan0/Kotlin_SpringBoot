package com.example.demo.controller;

import com.example.demo.config.GlobalExceptionHandler;
import com.example.demo.model.TimesheetEntry;
import com.example.demo.model.TimesheetSaveCommand;
import com.example.demo.service.TimesheetService;
import com.example.demo.service.TimesheetSummaryService;
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
import java.time.LocalTime;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = TimesheetControllerTest.TestApplication.class,
        properties = "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class TimesheetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final TimesheetService timesheetService;

    TimesheetControllerTest(
            @Autowired TimesheetService timesheetService,
            @Autowired TimesheetSummaryService summaryService
    ) {
        this.timesheetService = timesheetService;
    }

    @Test
    void saveEntryConvertsBlankIrregularWorkToClearFlag() throws Exception {
        TimesheetEntry saved = TimesheetEntry.builder().workDate(LocalDate.of(2026, 8, 30)).build();
        when(timesheetService.saveOrUpdateWithFlags(argThat(cmd ->
                cmd.clearIrregular() && cmd.noteProvided() && cmd.note() == null
        ))).thenReturn(saved);

        mockMvc.perform(post("/timesheet/api/entry")
                        .principal(new org.springframework.security.authentication.TestingAuthenticationToken("alice", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workDate": "2026-08-30",
                                  "irregularWorkType": "",
                                  "note": ""
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(timesheetService).saveOrUpdateWithFlags(argThat(TimesheetSaveCommand::clearIrregular));
    }

    @Test
    void updateNoteUsesExplicitEmptyValue() throws Exception {
        TimesheetEntry saved = TimesheetEntry.builder().note("").build();
        when(timesheetService.updateNote("alice", "")).thenReturn(saved);

        mockMvc.perform(post("/timesheet/api/note")
                        .principal(new org.springframework.security.authentication.TestingAuthenticationToken("alice", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").value(""));

        verify(timesheetService).updateNote("alice", "");
    }

    @Test
    void malformedDateIsHandledByAdvice() throws Exception {
        mockMvc.perform(post("/timesheet/api/entry")
                        .principal(new org.springframework.security.authentication.TestingAuthenticationToken("alice", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workDate\":\"invalid-date\"}"))
                .andExpect(status().isBadRequest());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({TimesheetController.class, GlobalExceptionHandler.class, MockBeans.class})
    static class TestApplication {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MockBeans {
        @Bean
        TimesheetService timesheetService() {
            return org.mockito.Mockito.mock(TimesheetService.class);
        }

        @Bean
        TimesheetSummaryService summaryService() {
            return org.mockito.Mockito.mock(TimesheetSummaryService.class);
        }
    }
}
