package com.example.demo.service

import com.example.demo.mapper.ContentItemMapper
import com.example.demo.model.ContentItem
import org.springframework.cache.annotation.Caching
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

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
        // 追加: ログインユーザー名を保存（未ログインはnullでグローバル）
        val auth = SecurityContextHolder.getContext().authentication
        val username =
            if (auth != null && auth.isAuthenticated && auth !is AnonymousAuthenticationToken) auth.name else null
        record.username = username
        val now = OffsetDateTime.now()
        if (record.createdAt == null) record.createdAt = now
        record.updatedAt = now
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
        // 追加: 更新者のユーザー名に上書き（要件により上書きしないなら削除）
        val auth = SecurityContextHolder.getContext().authentication
        val username =
            if (auth != null && auth.isAuthenticated && auth !is AnonymousAuthenticationToken) auth.name else null
        record.username = username
        record.updatedAt = OffsetDateTime.now()
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
