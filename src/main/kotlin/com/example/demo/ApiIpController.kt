package com.example.demo

import com.example.demo.mapper.AccessLogMapper
import com.example.demo.mapper.BlacklistEventMapper
import com.example.demo.mapper.BlacklistIpMapper
import com.example.demo.mapper.WhitelistIpMapper
import com.example.demo.model.BlacklistEvent
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/ip")
class ApiIpController(
    private val whitelistIpMapper: WhitelistIpMapper,
    private val blacklistIpMapper: BlacklistIpMapper,
    private val blacklistEventMapper: BlacklistEventMapper,
    private val accessLogMapper: AccessLogMapper,
) {
    data class BlacklistRequest(@field:NotBlank val ipAddress: String)
    data class AutoBlacklistResult(
        val totalCandidates: Int,
        val processed: Int
    )

    @GetMapping("/whitelist")
    fun listWhitelist(): ResponseEntity<Any> =
        ResponseEntity.ok(whitelistIpMapper.getActive())

    @GetMapping("/blacklist")
    fun listBlacklist(): ResponseEntity<Any> =
        ResponseEntity.ok(blacklistIpMapper.getAll())

    @PostMapping("/blacklist")
    fun addToBlacklist(@RequestBody req: BlacklistRequest, httpReq: HttpServletRequest): ResponseEntity<Void> {
        blacklistIpMapper.upsertIncrementTimes(req.ipAddress)
        whitelistIpMapper.markBlacklistedAndIncrement(req.ipAddress)
        // イベントを記録（画面/APIからの手動追加）
        try {
            val requestId = (httpReq.getAttribute("requestId") as? String) ?: UUID.randomUUID().toString()
            val path = httpReq.requestURI + (httpReq.queryString?.let { "?$it" } ?: "")
            blacklistEventMapper.insert(
                BlacklistEvent(
                    requestId = requestId,
                    ipAddress = req.ipAddress,
                    method = httpReq.method,
                    path = path,
                    status = HttpStatus.CREATED.value(),
                    userAgent = httpReq.getHeader("User-Agent"),
                    referer = httpReq.getHeader("Referer"),
                    reason = "MANUAL",
                    source = "API"
                )
            )
        } catch (_: Exception) {
        }
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @DeleteMapping("/blacklist/{id}")
    fun deleteFromBlacklist(@PathVariable id: Long): ResponseEntity<Void> {
        blacklistIpMapper.markDeletedById(id)
        return ResponseEntity.noContent().build()
    }

    // UA欠損IPの一括ブラックリスト化（手動トリガ用）
    @PostMapping("/auto-blacklist-ua-missing")
    fun autoBlacklistUaMissing(): ResponseEntity<AutoBlacklistResult> {
        val candidates = accessLogMapper.selectIpsWithMissingUserAgent()
        var processed = 0
        candidates.forEach { ip ->
            try {
                blacklistIpMapper.upsertIncrementTimes(ip)
                whitelistIpMapper.markBlacklistedAndIncrement(ip)
                // イベント記録（自動登録）
                try {
                    blacklistEventMapper.insert(
                        BlacklistEvent(
                            requestId = UUID.randomUUID().toString(),
                            ipAddress = ip,
                            method = "AUTO",
                            path = "/api/ip/auto-blacklist-ua-missing",
                            status = HttpStatus.CREATED.value(),
                            userAgent = null,
                            referer = null,
                            reason = "UA_MISSING",
                            source = "AUTO"
                        )
                    )
                } catch (_: Exception) {
                }
                processed++
            } catch (_: Exception) {
                // 1件ずつ継続
            }
        }
        return ResponseEntity.ok(AutoBlacklistResult(totalCandidates = candidates.size, processed = processed))
    }
}
