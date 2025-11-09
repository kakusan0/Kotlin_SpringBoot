package com.example.demo.mapper

import com.example.demo.model.BlacklistIp
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface BlacklistIpMapper {
    fun insert(ip: BlacklistIp): Int
    fun existsByIp(@Param("ipAddress") ipAddress: String): Boolean
    fun getAll(): List<BlacklistIp>
    fun upsertIncrementTimes(@Param("ipAddress") ipAddress: String): Int
    fun markDeletedById(@Param("id") id: Long): Int
}
