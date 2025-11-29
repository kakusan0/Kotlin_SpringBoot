package com.example.demo

import com.example.demo.mapper.*
import com.example.demo.model.UaBlacklistRule
import com.example.demo.service.BlacklistEventService
import com.example.demo.util.BlacklistEventFactory
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/ua-blacklist")
class ApiUaBlacklistController(
    private val ruleMapper: UaBlacklistRuleMapper,
    private val accessLogMapper: AccessLogMapper,
    private val blacklistIpMapper: BlacklistIpMapper,
    private val whitelistIpMapper: WhitelistIpMapper,
    private val blacklistEventMapper: BlacklistEventMapper,
    private val blacklistEventService: BlacklistEventService
) {
    data class CreateRuleRequest(
        @field:NotBlank val pattern: String,
        @field:NotBlank val matchType: String = "EXACT"
    )

    data class CreateRuleResponse(
        val rule: UaBlacklistRule,
        val blockedIpsCount: Int
    )

    @GetMapping
    fun list(): ResponseEntity<Any> = ResponseEntity.ok(ruleMapper.selectActive())

    @PostMapping
    fun create(@RequestBody req: CreateRuleRequest): ResponseEntity<Any> {
        val mt = req.matchType.uppercase()
        if (mt !in setOf("EXACT", "PREFIX", "REGEX")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(mapOf("message" to "matchType must be EXACT|PREFIX|REGEX"))
        }
        val rule = UaBlacklistRule(pattern = req.pattern, matchType = mt)
        ruleMapper.insert(rule)

        // 過去のアクセスログから該当するIPアドレスを取得してブロック
        var blockedCount = 0
        try {
            val matchingIps = accessLogMapper.selectIpsByUserAgentPattern(req.pattern, mt)
            matchingIps.forEach { ip ->
                try {
                    blacklistIpMapper.upsertIncrementTimes(ip)
                    whitelistIpMapper.markBlacklistedAndIncrement(ip)

                    // イベント記録（自動登録）
                    try {
                        // 非同期で挿入
                        blacklistEventService.recordEvent(
                            BlacklistEventFactory.create(
                                ipAddress = ip,
                                reason = "UA_RULE_${mt}",
                                source = "AUTO",
                                requestId = UUID.randomUUID().toString(),
                                method = "AUTO",
                                path = "/api/ua-blacklist",
                                status = HttpStatus.CREATED.value(),
                                userAgent = null,
                                referer = null
                            )
                        )
                    } catch (_: Exception) {
                    }
                    blockedCount++
                } catch (_: Exception) {
                    // 1件ずつ継続
                }
            }
        } catch (_: Exception) {
            // ログエラーがあっても、ルール自体は追加済みなので継続
        }

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(CreateRuleResponse(rule = rule, blockedIpsCount = blockedCount))
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        ruleMapper.logicalDelete(id)
        return ResponseEntity.noContent().build()
    }
}
