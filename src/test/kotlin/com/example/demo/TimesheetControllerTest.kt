package com.example.demo

import com.example.demo.model.TimesheetEntry
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class TimesheetControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc
    private val mapper = jacksonObjectMapper()

    @Test
    @WithMockUser(username = "user1")
    fun clockInAndOutFlow() {
        val inRes = mockMvc.perform(post("/timesheet/api/clock-in"))
            .andExpect(status().isOk)
            .andReturn()
        val entryIn: TimesheetEntry = mapper.readValue(inRes.response.contentAsString)
        assertEquals("user1", entryIn.userName)
        val outRes = mockMvc.perform(post("/timesheet/api/clock-out"))
            .andExpect(status().isOk)
            .andReturn()
        val entryOut: TimesheetEntry = mapper.readValue(outRes.response.contentAsString)
        assertEquals(entryIn.id, entryOut.id)
        assertEquals(entryIn.startTime, entryOut.startTime)
    }

    @Test
    @WithMockUser(username = "user1")
    fun todayEndpoint() {
        mockMvc.perform(post("/timesheet/api/clock-in"))
            .andExpect(status().isOk)
        val todayRes = mockMvc.perform(get("/timesheet/api/today"))
            .andExpect(status().isOk)
            .andReturn()
        val entry: TimesheetEntry = mapper.readValue(todayRes.response.contentAsString)
        assertEquals("user1", entry.userName)
    }
}
