package com.example.demo.mapper

import com.example.demo.model.ReportJob
import org.apache.ibatis.annotations.Mapper

@Mapper
interface ReportJobMapper {
    fun insert(job: ReportJob): Int
    fun selectById(id: Long): ReportJob?
    fun updateStatus(params: Map<String, Any?>): Int
    fun selectPending(): List<ReportJob>
}

