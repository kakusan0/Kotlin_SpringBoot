package com.example.demo.service

import com.example.demo.mapper.ContentItemMapper
import com.example.demo.model.ContentItem
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ContentItemService(
    private val contentItemMapper: ContentItemMapper
) {
    @Cacheable(cacheNames = ["contentItems"])
    fun getAll(): List<ContentItem> = contentItemMapper.selectAll()

    fun getAllForHome(): List<ContentItem> = contentItemMapper.selectAllForHome()

    @Cacheable(cacheNames = ["contentItemsByMenu"], key = "#menuName")
    fun getByMenuName(menuName: String): List<ContentItem> =
        contentItemMapper.selectByMenuName(menuName)

    @Transactional
    @Caching(
        evict = [
            CacheEvict(cacheNames = ["contentItems"], allEntries = true),
            CacheEvict(cacheNames = ["contentItemsByMenu"], allEntries = true),
            CacheEvict(cacheNames = ["contentItemById"], key = "#record.id", condition = "#record.id != null")
        ]
    )
    fun insert(record: ContentItem): Int {
        record.pathName = record.pathName?.trim()?.takeIf { it.isNotEmpty() }
        return contentItemMapper.insert(record)
    }

    @Transactional
    @Caching(
        evict = [
            CacheEvict(cacheNames = ["contentItems"], allEntries = true),
            CacheEvict(cacheNames = ["contentItemsByMenu"], allEntries = true),
            CacheEvict(cacheNames = ["contentItemById"], key = "#record.id", condition = "#record.id != null")
        ]
    )
    fun update(record: ContentItem): Int {
        record.pathName = record.pathName?.trim()?.takeIf { it.isNotEmpty() }
        return contentItemMapper.updateByPrimaryKey(record)
    }

    @Transactional
    @Caching(
        evict = [
            CacheEvict(cacheNames = ["contentItems"], allEntries = true),
            CacheEvict(cacheNames = ["contentItemsByMenu"], allEntries = true),
            CacheEvict(cacheNames = ["contentItemById"], key = "#id")
        ]
    )
    fun delete(id: Long): Int = contentItemMapper.deleteByPrimaryKey(id)
}
