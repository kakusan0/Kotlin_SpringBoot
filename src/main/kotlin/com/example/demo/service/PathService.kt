package com.example.demo.service

import com.example.demo.mapper.PathMapper
import com.example.demo.model.Path
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service

@Service
class PathService(
    private val pathMapper: PathMapper
) {
    @Cacheable(cacheNames = ["pathsActive"])
    fun getAllActive(): List<Path> = pathMapper.selectAllActive()

    @Cacheable(cacheNames = ["pathsAll"])
    fun getAllIncludingDeleted(): List<Path> = pathMapper.selectAllIncludingDeleted()

    @Cacheable(cacheNames = ["pathById"], key = "#id")
    fun getById(id: Long): Path? = pathMapper.selectByPrimaryKey(id)

    @Caching(
        evict = [
            CacheEvict(cacheNames = ["pathsActive", "pathsAll"], allEntries = true),
            CacheEvict(cacheNames = ["pathById"], key = "#record.id", condition = "#record.id != null")
        ]
    )
    fun insert(record: Path): Int = pathMapper.insert(record)

    @Caching(
        evict = [
            CacheEvict(cacheNames = ["pathsActive", "pathsAll"], allEntries = true),
            CacheEvict(cacheNames = ["pathById"], key = "#record.id", condition = "#record.id != null")
        ]
    )
    fun update(record: Path): Int = pathMapper.updateByPrimaryKey(record)

    @Caching(
        evict = [
            CacheEvict(cacheNames = ["pathsActive", "pathsAll"], allEntries = true),
            CacheEvict(cacheNames = ["pathById"], key = "#id")
        ]
    )
    fun logicalDelete(id: Long): Int = pathMapper.logicalDelete(id)

    @Caching(
        evict = [
            CacheEvict(cacheNames = ["pathsActive", "pathsAll"], allEntries = true),
            CacheEvict(cacheNames = ["pathById"], key = "#id")
        ]
    )
    fun restore(id: Long): Int = pathMapper.restore(id)
}

