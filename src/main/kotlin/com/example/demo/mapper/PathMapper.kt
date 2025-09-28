package com.example.demo.mapper

import com.example.demo.model.Path
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface PathMapper {
    fun selectAllActive(): List<Path>
    fun selectAllIncludingDeleted(): List<Path>
    fun selectByPrimaryKey(id: Long): Path?
    fun insert(record: Path): Int
    fun updateByPrimaryKey(record: Path): Int
    fun logicalDelete(@Param("id") id: Long): Int
    fun restore(@Param("id") id: Long): Int
}

