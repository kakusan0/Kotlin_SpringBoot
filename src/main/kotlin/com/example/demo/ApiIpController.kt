package com.example.demo

import com.example.demo.mapper.BlacklistIpMapper
import com.example.demo.mapper.WhitelistIpMapper
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/ip")
class ApiIpController(
    private val whitelistIpMapper: WhitelistIpMapper,
    private val blacklistIpMapper: BlacklistIpMapper
) {
    data class BlacklistRequest(@field:NotBlank val ipAddress: String)

    @GetMapping("/whitelist")
    fun listWhitelist(): ResponseEntity<Any> =
        ResponseEntity.ok(whitelistIpMapper.getAll())

    @GetMapping("/blacklist")
    fun listBlacklist(): ResponseEntity<Any> =
        ResponseEntity.ok(blacklistIpMapper.getAll())

    @PostMapping("/blacklist")
    fun addToBlacklist(@RequestBody req: BlacklistRequest): ResponseEntity<Void> {
        blacklistIpMapper.upsertIncrementTimes(req.ipAddress)
        whitelistIpMapper.markBlacklistedAndIncrement(req.ipAddress)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @DeleteMapping("/blacklist/{id}")
    fun deleteFromBlacklist(@PathVariable id: Long): ResponseEntity<Void> {
        blacklistIpMapper.markDeletedById(id)
        return ResponseEntity.noContent().build()
    }
}
