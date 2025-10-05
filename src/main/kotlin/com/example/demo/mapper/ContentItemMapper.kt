package com.example.demo.mapper

import com.example.demo.model.ContentItem
import org.apache.ibatis.annotations.Mapper

@Mapper
interface ContentItemMapper {
    fun selectAll(): List<ContentItem>
    fun selectAllForHome(): List<ContentItem>
    fun selectByPrimaryKey(id: Long): ContentItem?
    fun selectByMenuName(menuName: String): List<ContentItem>
    fun insert(record: ContentItem): Int
    fun updateByPrimaryKey(record: ContentItem): Int
    fun deleteByPrimaryKey(id: Long): Int
}
