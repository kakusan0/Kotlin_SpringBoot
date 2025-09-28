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
    // 画面一覧は頻繁に参照されるためキャッシュ
    @Cacheable(cacheNames = ["contentItems"]) // 引数なし: SimpleKey.EMPTY がキー
    fun getAll(): List<ContentItem> = contentItemMapper.selectAll()

    @Cacheable(cacheNames = ["contentItemById"], key = "#id")
    fun getById(id: Long): ContentItem? = contentItemMapper.selectByPrimaryKey(id)

    // 追加: menuName でフィルタした一覧取得
    // Mapper 側の selectByMenuName が環境によって未解決になることがあるため、安全のため selectAll をフィルタして返す実装にする
    fun getByMenuName(menuName: String): List<ContentItem> = contentItemMapper.selectAll().filter { it.menuName == menuName }

    @Caching(
        evict = [
            CacheEvict(cacheNames = ["contentItems"], allEntries = true),
            CacheEvict(cacheNames = ["contentItemById"], key = "#record.id", condition = "#record.id != null")
        ]
    )
    fun insert(record: ContentItem): Int = contentItemMapper.insert(record)

    @Caching(
        evict = [
            CacheEvict(cacheNames = ["contentItems"], allEntries = true),
            CacheEvict(cacheNames = ["contentItemById"], key = "#record.id", condition = "#record.id != null")
        ]
    )
    fun update(record: ContentItem): Int = contentItemMapper.updateByPrimaryKey(record)

    @Caching(
        evict = [
            CacheEvict(cacheNames = ["contentItems"], allEntries = true),
            CacheEvict(cacheNames = ["contentItemById"], key = "#id")
        ]
    )
    fun delete(id: Long): Int = contentItemMapper.deleteByPrimaryKey(id)
}
