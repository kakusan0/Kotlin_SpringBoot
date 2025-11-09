package com.example.demo.mapper

import com.example.demo.model.WhitelistIp
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface WhitelistIpMapper {
    fun insert(ip: WhitelistIp): Int
    fun existsByIp(@Param("ipAddress") ipAddress: String): Boolean
    fun getAll(): List<WhitelistIp>
    fun getActive(): List<WhitelistIp>
    fun markBlacklistedAndIncrement(@Param("ipAddress") ipAddress: String): Int
}
