package com.example.demo.service

import com.example.demo.mapper.BlacklistEventMapper
import com.example.demo.model.BlacklistEvent
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class BlacklistEventService(
    private val blacklistEventMapper: BlacklistEventMapper
) {
    companion object {
        private val log = LoggerFactory.getLogger(BlacklistEventService::class.java)
    }

    @Async("taskExecutor")
    fun recordEvent(event: BlacklistEvent) {
        try {
            blacklistEventMapper.insert(event)
        } catch (ex: Exception) {
            // 非同期失敗はログに残して swallow
            log.warn(
                "非同期ブラックリストイベント保存に失敗: ip={}, reason={}, err={}",
                event.ipAddress,
                event.reason,
                ex.toString()
            )
        }
    }

    // 同期的に呼びたい場合用
    fun recordEventSync(event: BlacklistEvent) {
        blacklistEventMapper.insert(event)
    }
}

