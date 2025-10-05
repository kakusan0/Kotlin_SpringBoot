package com.example.demo.service

import com.example.demo.mapper.MenuMapper
import com.example.demo.model.Menu
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service

@Service
class MenuService(
    private val menuMapper: MenuMapper
) {
    @Cacheable(cacheNames = ["menus"])
    fun getAll(): List<Menu> = menuMapper.selectAll()

    @Cacheable(cacheNames = ["menuById"], key = "#id")
    fun getById(id: Long): Menu? = menuMapper.selectByPrimaryKey(id)

    @Caching(
        evict = [
            CacheEvict(cacheNames = ["menus"], allEntries = true),
            CacheEvict(cacheNames = ["menuById"], key = "#record.id", condition = "#record.id != null")
        ]
    )
    fun insert(record: Menu): Int = menuMapper.insert(record)

    @Caching(
        evict = [
            CacheEvict(cacheNames = ["menus"], allEntries = true),
            CacheEvict(cacheNames = ["menuById"], key = "#record.id", condition = "#record.id != null")
        ]
    )
    fun update(record: Menu): Int = menuMapper.updateByPrimaryKey(record)

    @Caching(
        evict = [
            CacheEvict(cacheNames = ["menus"], allEntries = true),
            CacheEvict(cacheNames = ["menuById"], key = "#id")
        ]
    )
    fun delete(id: Long): Int = menuMapper.deleteByPrimaryKey(id)
}

