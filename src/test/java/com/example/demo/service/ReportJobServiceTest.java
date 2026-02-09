package com.example.demo.service;

import com.example.demo.mapper.ReportJobMapper;
import com.example.demo.model.ReportJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportJobServiceTest {

    @TempDir
    Path tempDir;
    @Mock
    private ReportJobMapper reportJobMapper;
    @Mock
    private ReportService reportService;

    @Test
    void asyncRunWritesReportAndUpdatesStatus() throws Exception {
        ReportJobService service = new ReportJobService(reportJobMapper, reportService, tempDir.toString());

        ReportJob job = ReportJob.builder()
                .id(1L)
                .username("user")
                .fromDate(LocalDate.of(2026, 2, 1))
                .toDate(LocalDate.of(2026, 2, 2))
                .format("xlsx")
                .build();

        when(reportJobMapper.selectById(1L)).thenReturn(job);
        when(reportService.generateXlsxBytes(eq("user"), eq(job.getFromDate()), eq(job.getToDate())))
                .thenReturn("dummy".getBytes());

        service.asyncRun(1L);

        ArgumentCaptor<Map<String, Object>> statusCaptor = ArgumentCaptor.forClass(Map.class);
        InOrder inOrder = inOrder(reportJobMapper);
        inOrder.verify(reportJobMapper).updateStatus(statusCaptor.capture());
        inOrder.verify(reportJobMapper).updateStatus(statusCaptor.capture());

        Map<String, Object> running = statusCaptor.getAllValues().get(0);
        Map<String, Object> done = statusCaptor.getAllValues().get(1);

        assertEquals("RUNNING", running.get("status"));
        assertEquals("DONE", done.get("status"));
        assertInstanceOf(String.class, done.get("filePath"));
        assertTrue(Files.exists(Path.of(done.get("filePath").toString())));

        verify(reportService, times(1))
                .generateXlsxBytes(eq("user"), eq(job.getFromDate()), eq(job.getToDate()));
        verify(reportJobMapper, times(1)).selectById(1L);
    }

    @Test
    void submitJobInsertsAndTriggersAsyncRun() {
        ReportJobService service = spy(new ReportJobService(reportJobMapper, reportService, tempDir.toString()));

        doAnswer(invocation -> {
            ReportJob job = invocation.getArgument(0);
            job.setId(99L);
            return null;
        }).when(reportJobMapper).insert(any(ReportJob.class));

        Long jobId = service.submitJob("user", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 2), "xlsx");

        assertEquals(99L, jobId);
        verify(reportJobMapper, times(1)).insert(any(ReportJob.class));
        verify(service, times(1)).asyncRun(99L);
    }
}

