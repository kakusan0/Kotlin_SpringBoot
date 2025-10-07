package com.example.demo

import com.example.demo.mapper.BlacklistIpMapper
import com.example.demo.mapper.WhitelistIpMapper
import com.example.demo.mapper.BlacklistEventMapper
import com.example.demo.model.BlacklistEvent
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/ip")
class ApiIpController(
    private val whitelistIpMapper: WhitelistIpMapper,
    private val blacklistIpMapper: BlacklistIpMapper,
    private val blacklistEventMapper: BlacklistEventMapper
) {
    data class BlacklistRequest(@field:NotBlank val ipAddress: String)

    @GetMapping("/whitelist")
    fun listWhitelist(): ResponseEntity<Any> =
        ResponseEntity.ok(whitelistIpMapper.getAll())

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
        } catch (_: Exception) { }
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @DeleteMapping("/blacklist/{id}")
    fun deleteFromBlacklist(@PathVariable id: Long): ResponseEntity<Void> {
        blacklistIpMapper.markDeletedById(id)
        return ResponseEntity.noContent().build()
    }
}
