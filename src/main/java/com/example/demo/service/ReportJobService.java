package com.example.demo.service;

import com.example.demo.mapper.ReportJobMapper;
import com.example.demo.model.ReportJob;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportJobService {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ReportJobService.class);

    private final ReportJobMapper reportJobMapper;
    private final ReportService reportService;
    private final String reportDir;

    public ReportJobService(
            ReportJobMapper reportJobMapper,
            ReportService reportService,
            @Value("${app.report.dir:reports}") String reportDir
    ) {
        this.reportJobMapper = reportJobMapper;
        this.reportService = reportService;
        this.reportDir = reportDir != null ? reportDir : "reports";
    }

    public Long submitJob(String username, LocalDate from, LocalDate to, String format) {
        ReportJob job = new ReportJob(null, username, from, to, format, "PENDING", null, null, null, null);
        reportJobMapper.insert(job);
        logger.info("Report job submitted id={} user={} range={}..{} format={}", job.getId(), username, from, to, format);
        asyncRun(job.getId());
        return job.getId();
    }

    @Async
    public void asyncRun(Long jobId) {
        ReportJob job = reportJobMapper.selectById(jobId);
        if (job == null) {
            return;
        }
        try {
            Map<String, Object> running = new HashMap<>();
            running.put("id", jobId);
            running.put("status", "RUNNING");
            running.put("filePath", null);
            running.put("errorMessage", null);
            reportJobMapper.updateStatus(running);

            byte[] bytes = reportService.generateXlsxBytes(job.getUsername(), job.getFromDate(), job.getToDate());

            DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
            String fname = "timesheet_" + job.getUsername() + "_" + job.getFromDate() + "_" + job.getToDate()
                    + "_" + df.format(OffsetDateTime.now()) + ".xlsx";
            File dir = new File(reportDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File f = new File(dir, fname);
            java.nio.file.Files.write(f.toPath(), bytes);

            Map<String, Object> done = new HashMap<>();
            done.put("id", jobId);
            done.put("status", "DONE");
            done.put("filePath", f.getAbsolutePath());
            done.put("errorMessage", null);
            reportJobMapper.updateStatus(done);
        } catch (Throwable ex) {
            logger.error("Report job failed id={}", jobId, ex);
            Map<String, Object> failed = new HashMap<>();
            failed.put("id", jobId);
            failed.put("status", "FAILED");
            failed.put("filePath", null);
            failed.put("errorMessage", ex.getMessage());
            reportJobMapper.updateStatus(failed);
        }
    }

    public ReportJob getJob(Long id) {
        return reportJobMapper.selectById(id);
    }
}
