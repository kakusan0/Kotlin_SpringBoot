package com.example.demo.service

import com.example.demo.mapper.ContentItemMapper
import com.example.demo.model.ContentItem
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service

@Service
class ContentItemService(
    private val contentItemMapper: ContentItemMapper
) {
    // 管理画面用：削除済みメニュー/パスも含めてすべて取得
    @Cacheable(cacheNames = ["contentItems"])
    fun getAll(): List<ContentItem> = contentItemMapper.selectAll()

    // ホームページ用：削除されていないメニュー/パスのみ取得（キャッシュなし）
    fun getAllForHome(): List<ContentItem> = contentItemMapper.selectAllForHome()

    // 追加: menuName でフィルタした一覧取得
    // Mapper 側に selectByMenuName があるため、DB側で絞る方が効率的
    @Cacheable(cacheNames = ["contentItemsByMenu"], key = "#menuName")
    fun getByMenuName(menuName: String): List<ContentItem> = contentItemMapper.selectByMenuName(menuName)

    private fun normalizePathName(record: ContentItem) {
        record.pathName = record.pathName?.trim()?.takeIf { it.isNotEmpty() }
    }

    @Caching(
        evict = [
            CacheEvict(cacheNames = ["contentItems"], allEntries = true),
            CacheEvict(cacheNames = ["contentItemById"], key = "#record.id", condition = "#record.id != null")
        ]
    )
    fun insert(record: ContentItem): Int {
        normalizePathName(record)
        return contentItemMapper.insert(record)
    }

    @Caching(
        evict = [
            CacheEvict(cacheNames = ["contentItems"], allEntries = true),
            CacheEvict(cacheNames = ["contentItemById"], key = "#record.id", condition = "#record.id != null")
        ]
    )
    fun update(record: ContentItem): Int {
        normalizePathName(record)
        return contentItemMapper.updateByPrimaryKey(record)
    }

    @Caching(
        evict = [
            CacheEvict(cacheNames = ["contentItems"], allEntries = true),
            CacheEvict(cacheNames = ["contentItemById"], key = "#id")
        ]
    )
    fun delete(id: Long): Int = contentItemMapper.deleteByPrimaryKey(id)
}
