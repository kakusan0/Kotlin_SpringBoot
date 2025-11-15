package com.example.demo.service

import com.example.demo.mapper.PathMapper
import com.example.demo.model.Path
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PathService(
    private val pathMapper: PathMapper
) {
    // 一覧は最新性重視のためキャッシュしない
    fun getAllActive(): List<Path> = pathMapper.selectAllActive()

    fun getAllIncludingDeleted(): List<Path> = pathMapper.selectAllIncludingDeleted()

    @Cacheable(cacheNames = ["pathById"], key = "#id")
    fun getById(id: Long): Path? = pathMapper.selectByPrimaryKey(id)

    @Transactional
    @Caching(
        evict = [
            CacheEvict(cacheNames = ["pathById"], key = "#record.id", condition = "#record.id != null")
        ]
    )
    fun insert(record: Path): Int = pathMapper.insert(record)

    @Transactional
    @Caching(
        evict = [
            CacheEvict(cacheNames = ["pathById"], key = "#record.id", condition = "#record.id != null")
        ]
    )
    fun update(record: Path): Int = pathMapper.updateByPrimaryKey(record)

    @Transactional
    @Caching(
        evict = [
            CacheEvict(cacheNames = ["pathById"], key = "#id")
        ]
    )
    fun logicalDelete(id: Long): Int = pathMapper.logicalDelete(id)

    @Transactional
    @Caching(
        evict = [
            CacheEvict(cacheNames = ["pathById"], key = "#id")
        ]
    )
    fun restore(id: Long): Int = pathMapper.restore(id)
}
