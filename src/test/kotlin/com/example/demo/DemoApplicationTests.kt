package com.example.demo

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.hamcrest.Matchers.containsString

@SpringBootTest
@AutoConfigureMockMvc
class DemoApplicationTests {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun contextLoads() {
    }

    @Test
    fun robotsTxt_isServed() {
        mockMvc.perform(get("/robots.txt"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("User-agent")))
    }

    @Test
    fun robotsTxt_containsAiCrawlerBlocks() {
        mockMvc.perform(get("/robots.txt"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("User-agent: GPTBot")))
            .andExpect(content().string(containsString("User-agent: Google-Extended")))
            .andExpect(content().string(containsString("User-agent: Microsoft-Extended")))
            .andExpect(content().string(containsString("User-agent: ClaudeBot")))
            .andExpect(content().string(containsString("User-agent: PerplexityBot")))
    }
}
