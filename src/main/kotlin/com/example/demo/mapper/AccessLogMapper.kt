package com.example.demo.mapper

import com.example.demo.model.AccessLog
import org.apache.ibatis.annotations.Mapper

@Mapper
interface AccessLogMapper {
    fun insert(record: AccessLog): Int
}

