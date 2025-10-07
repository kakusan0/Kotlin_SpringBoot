package com.example.demo.mapper

import com.example.demo.model.UaBlacklistRule
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface UaBlacklistRuleMapper {
    fun selectActive(): List<UaBlacklistRule>
    fun insert(rule: UaBlacklistRule): Int
    fun logicalDelete(@Param("id") id: Long): Int
}

