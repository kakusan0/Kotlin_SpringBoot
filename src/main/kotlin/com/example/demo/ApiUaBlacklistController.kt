package com.example.demo

import com.example.demo.mapper.UaBlacklistRuleMapper
import com.example.demo.model.UaBlacklistRule
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/ua-blacklist")
class ApiUaBlacklistController(
    private val ruleMapper: UaBlacklistRuleMapper
) {
    data class CreateRuleRequest(
        @field:NotBlank val pattern: String,
        @field:NotBlank val matchType: String = "EXACT"
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
        return ResponseEntity.status(HttpStatus.CREATED).body(rule)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        ruleMapper.logicalDelete(id)
        return ResponseEntity.noContent().build()
    }
}

