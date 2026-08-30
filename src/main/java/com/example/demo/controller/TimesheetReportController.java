package com.example.demo.controller;

import com.example.demo.model.ReportJob;
import com.example.demo.service.ReportJobService;
import com.example.demo.service.ReportService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static java.lang.StringTemplate.STR;

@RestController
@RequestMapping("/timesheet/report")
@Validated
@RequiredArgsConstructor
public class TimesheetReportController {

    private final ReportJobService reportJobService;
    private final ReportService reportService;


    @GetMapping("/xlsx")
    public ResponseEntity<byte[]> xlsx(
            @RequestParam @NotBlank String username,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Principal principal
    ) {
        if (!principal.getName().equals(username)) {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            boolean hasAdmin = auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            if (!hasAdmin) return ResponseEntity.status(403).build();
        }
        byte[] bytes = reportService.generateXlsxBytes(username, from, to);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        String safeNameXlsx = URLEncoder.encode(STR."timesheet_\{username}_\{from}_to_\{to}.xlsx", StandardCharsets.UTF_8);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, STR."attachment; filename*=UTF-8''\{safeNameXlsx}");
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf(
            @RequestParam @NotBlank String username,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Principal principal
    ) {
        if (!principal.getName().equals(username)) {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            boolean hasAdmin = auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            if (!hasAdmin) return ResponseEntity.status(403).build();
        }
        byte[] bytes = reportService.generatePdfBytes(username, from, to);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        String safeNamePdf = URLEncoder.encode(STR."timesheet_\{username}_\{from}_to_\{to}.pdf", StandardCharsets.UTF_8);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, STR."attachment; filename*=UTF-8''\{safeNamePdf}");
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    @GetMapping("/submit")
    public ResponseEntity<Object> submit(
            @RequestParam @NotBlank String username,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam @Pattern(regexp = "(?i)xlsx|pdf") String format,
            Principal principal
    ) {
        if (!principal.getName().equals(username)) {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            boolean hasAdmin = auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            if (!hasAdmin) return ResponseEntity.status(403).build();
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days <= 31) {
            final byte[] bytes;
            final MediaType contentType;
            final String filename;
            switch (format.toLowerCase()) {
                case "xlsx" -> {
                    bytes = reportService.generateXlsxBytes(username, from, to);
                    contentType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    filename = STR."timesheet_\{username}_\{from}_to_\{to}.xlsx";
                }
                case "pdf" -> {
                    bytes = reportService.generatePdfBytes(username, from, to);
                    contentType = MediaType.APPLICATION_PDF;
                    filename = STR."timesheet_\{username}_\{from}_to_\{to}.pdf";
                }
                default -> {
                    return ResponseEntity.badRequest().body("unsupported format");
                }
            }
            String safeName = URLEncoder.encode(filename, StandardCharsets.UTF_8);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(contentType);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, STR."attachment; filename*=UTF-8''\{safeName}");
            return ResponseEntity.ok().headers(headers).body(bytes);
        }
        Long jobId = reportJobService.submitJob(username, from, to, format);
        return ResponseEntity.accepted().body(Map.of("jobId", jobId));
    }

    @GetMapping("/job/{id}")
    public ResponseEntity<Object> jobStatus(@PathVariable Long id, Principal principal) {
        ReportJob job = reportJobService.getJob(id);
        if (job == null) return ResponseEntity.notFound().build();
        if (!principal.getName().equals(job.getUsername())) {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            boolean hasAdmin = auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            if (!hasAdmin) return ResponseEntity.status(403).build();
        }
        Map<String, Object> resp = Map.of(
                "id", job.getId(),
                "status", job.getStatus(),
                "filePath", job.getFilePath(),
                "errorMessage", job.getErrorMessage()
        );
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/job/{id}/download")
    public ResponseEntity<Object> jobDownload(@PathVariable Long id, Principal principal) throws IOException {
        ReportJob job = reportJobService.getJob(id);
        if (job == null) return ResponseEntity.notFound().build();
        if (!principal.getName().equals(job.getUsername())) {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            boolean hasAdmin = auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            if (!hasAdmin) return ResponseEntity.status(403).build();
        }
        if (!"DONE".equals(job.getStatus()) || job.getFilePath() == null || job.getFilePath().isBlank()) {
            return ResponseEntity.status(409).body(Map.of("status", job.getStatus()));
        }
        File f = new File(job.getFilePath());
        if (!f.exists()) return ResponseEntity.notFound().build();
        byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
        HttpHeaders headers = new HttpHeaders();
        MediaType contentType = f.getName().endsWith(".pdf")
                ? MediaType.APPLICATION_PDF
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        headers.setContentType(contentType);
        String safeName = URLEncoder.encode(f.getName(), StandardCharsets.UTF_8);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + safeName);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    @GetMapping("/uniss-xlsx")
    public ResponseEntity<byte[]> unissXlsx(
            @RequestParam @NotBlank String username,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Principal principal
    ) {
        if (!principal.getName().equals(username)) {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            boolean hasAdmin = auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            if (!hasAdmin) return ResponseEntity.status(403).build();
        }
        byte[] bytes = reportService.generateUnissXlsxBytes(username, from, to);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        String safeNameXlsx = URLEncoder.encode(
                STR."\{from.getYear()}年\{String.format("%02d", from.getMonthValue())}月度UNISS勤務表(\{username}).xlsx",
                StandardCharsets.UTF_8
        );
        headers.set(HttpHeaders.CONTENT_DISPOSITION, STR."attachment; filename*=UTF-8''\{safeNameXlsx}");
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}
