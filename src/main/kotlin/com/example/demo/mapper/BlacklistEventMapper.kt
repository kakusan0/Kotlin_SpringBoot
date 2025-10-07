package com.example.demo.mapper

import com.example.demo.model.BlacklistEvent
import org.apache.ibatis.annotations.Mapper

@Mapper
interface BlacklistEventMapper {
    fun insert(event: BlacklistEvent): Int
}

