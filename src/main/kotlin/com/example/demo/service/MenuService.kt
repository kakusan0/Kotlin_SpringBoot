package com.example.demo.service

import com.example.demo.mapper.MenuMapper
import com.example.demo.model.Menu
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class MenuService(
    private val menuMapper: MenuMapper
) {
    // ホームページ表示用：削除されていないメニューのみ取得（キャッシュなしのまま）
    fun getAll(): List<Menu> = menuMapper.selectAll()

    // 管理画面で削除済みも含む一覧は最新性重視のためキャッシュしない
    fun getAllIncludingDeleted(): List<Menu> = menuMapper.selectAllIncludingDeleted()

    @Cacheable(cacheNames = ["menuById"], key = "#id")
    fun getById(id: Long): Menu? = menuMapper.selectByPrimaryKey(id)

    @Transactional
    @Caching(
        evict = [
            // 一覧はキャッシュしていないため一覧系のエビクトは不要
            CacheEvict(cacheNames = ["menuById"], key = "#record.id", condition = "#record.id != null")
        ]
    )
    fun insert(record: Menu): Int = menuMapper.insert(record)

    @Transactional
    @Caching(
        evict = [
            CacheEvict(cacheNames = ["menuById"], key = "#record.id", condition = "#record.id != null")
        ]
    )
    fun update(record: Menu): Int = menuMapper.updateByPrimaryKey(record)

    @Transactional
    @Caching(
        evict = [
            CacheEvict(cacheNames = ["menuById"], key = "#id")
        ]
    )
    fun delete(id: Long): Int = menuMapper.logicalDeleteByPrimaryKey(id)
}
