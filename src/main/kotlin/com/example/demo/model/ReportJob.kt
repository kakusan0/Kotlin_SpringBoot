package com.example.demo.model

import java.time.OffsetDateTime

class ReportJob(
    var id: Long? = null,
    var username: String,
    var fromDate: java.time.LocalDate,
    var toDate: java.time.LocalDate,
    var format: String,
    var status: String = "PENDING",
    var filePath: String? = null,
    var errorMessage: String? = null,
    var createdAt: OffsetDateTime? = null,
    var updatedAt: OffsetDateTime? = null
)

