package com.example.demo.mapper

import com.example.demo.model.Menu
import org.apache.ibatis.annotations.Mapper

@Mapper
interface MenuMapper {
    fun selectAll(): List<Menu>
    fun selectByPrimaryKey(id: Long): Menu?
    fun insert(record: Menu): Int
    fun updateByPrimaryKey(record: Menu): Int
    fun deleteByPrimaryKey(id: Long): Int
}
