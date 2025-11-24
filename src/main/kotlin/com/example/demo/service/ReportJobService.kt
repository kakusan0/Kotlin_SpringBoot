package com.example.demo.service

import com.example.demo.mapper.ReportJobMapper
import com.example.demo.model.ReportJob
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class ReportJobService(
    private val reportJobMapper: ReportJobMapper,
    private val reportService: ReportService,
    @Value("\${app.report.dir:reports}")
    private val reportDir: String = "reports"
) {
    private val logger = LoggerFactory.getLogger(ReportJobService::class.java)

    fun submitJob(username: String, from: LocalDate, to: LocalDate, format: String): Long {
        val job = ReportJob(username = username, fromDate = from, toDate = to, format = format)
        reportJobMapper.insert(job)
        logger.info("Report job submitted id=${job.id} user=$username range=$from..$to format=$format")
        asyncRun(job.id!!)
        return job.id!!
    }

    @Async
    fun asyncRun(jobId: Long) {
        val job = reportJobMapper.selectById(jobId) ?: return
        try {
            reportJobMapper.updateStatus(
                mapOf(
                    "id" to jobId,
                    "status" to "RUNNING",
                    "filePath" to null,
                    "errorMessage" to null
                )
            )
            val bytes = when (job.format.lowercase()) {
                "csv" -> reportService.generateCsvBytes(job.username, job.fromDate, job.toDate)
                "pdf" -> reportService.generatePdfBytes(job.username, job.fromDate, job.toDate)
                "xlsx" -> reportService.generateXlsxBytes(job.username, job.fromDate, job.toDate)
                else -> throw IllegalArgumentException("unsupported format: ${job.format}")
            }
            val df = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            val fname =
                "timesheet_${job.username}_${job.fromDate}_${job.toDate}_" + df.format(java.time.OffsetDateTime.now()) + when (job.format.lowercase()) {
                    "csv" -> ".csv"; "pdf" -> ".pdf"; else -> ".xlsx"
                }
            val dir = File(reportDir)
            if (!dir.exists()) dir.mkdirs()
            val f = File(dir, fname)
            f.writeBytes(bytes)
            reportJobMapper.updateStatus(
                mapOf(
                    "id" to jobId,
                    "status" to "DONE",
                    "filePath" to f.absolutePath,
                    "errorMessage" to null
                )
            )
        } catch (ex: Throwable) {
            logger.error("Report job failed id=$jobId", ex)
            reportJobMapper.updateStatus(
                mapOf(
                    "id" to jobId,
                    "status" to "FAILED",
                    "filePath" to null,
                    "errorMessage" to ex.message
                )
            )
        }
    }

    fun getJob(id: Long): ReportJob? = reportJobMapper.selectById(id)
}
