package com.example.demo.service

import com.example.demo.mapper.ContentItemMapper
import com.example.demo.model.ContentItem
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ContentItemService(
    private val contentItemMapper: ContentItemMapper
) {
    // 一覧は最新性重視のためキャッシュしない
    fun getAll(): List<ContentItem> = contentItemMapper.selectAll()

    fun getAllForHome(): List<ContentItem> = contentItemMapper.selectAllForHome()

    // メニュー名での一覧もキャッシュしない（管理画面更新の即時反映を優先）
    fun getByMenuName(menuName: String): List<ContentItem> =
        contentItemMapper.selectByMenuName(menuName)

    @Transactional
    @Caching(
        evict = [
            // 一覧キャッシュを使っていないので全件エビクトは不要だが、将来の安全性のため残すならコメントアウト解除
            // CacheEvict(cacheNames = ["contentItems", "contentItemsByMenu"], allEntries = true)
        ]
    )
    fun insert(record: ContentItem): Int {
        record.pathName = record.pathName?.trim()?.takeIf { it.isNotEmpty() }
        return contentItemMapper.insert(record)
    }

    @Transactional
    @Caching(
        evict = [
            // CacheEvict(cacheNames = ["contentItems", "contentItemsByMenu"], allEntries = true)
        ]
    )
    fun update(record: ContentItem): Int {
        record.pathName = record.pathName?.trim()?.takeIf { it.isNotEmpty() }
        return contentItemMapper.updateByPrimaryKey(record)
    }

    @Transactional
    @Caching(
        evict = [
            // CacheEvict(cacheNames = ["contentItems", "contentItemsByMenu"], allEntries = true)
        ]
    )
    fun delete(id: Long): Int = contentItemMapper.deleteByPrimaryKey(id)
}
