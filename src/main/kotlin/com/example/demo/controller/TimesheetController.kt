package com.example.demo.controller

import com.example.demo.model.TimesheetEntry
import com.example.demo.service.TimesheetConflictException
import com.example.demo.service.TimesheetNotFoundException
import com.example.demo.service.TimesheetService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

@RestController
@RequestMapping("/timesheet/api")
class TimesheetController(
    private val timesheetService: TimesheetService
) {
    private val emitters = CopyOnWriteArrayList<SseEmitter>()

    @PostMapping("/clock-in")
    fun clockIn(auth: Authentication): TimesheetEntry {
        val entry = timesheetService.clockIn(auth.name)
        broadcast("clock-in", entry)
        return entry
    }

    @PostMapping("/clock-out")
    fun clockOut(auth: Authentication): TimesheetEntry {
        val entry = timesheetService.clockOut(auth.name)
        broadcast("clock-out", entry)
        return entry
    }

    @GetMapping("/today")
    fun today(auth: Authentication): TimesheetEntry? = timesheetService.getToday(auth.name)

    @GetMapping
    fun list(auth: Authentication, @RequestParam from: String, @RequestParam to: String): List<TimesheetEntry> {
        // 互換性維持のため旧パス(GET /timesheet/api?from=...&to=...)も利用可能
        val fromDate = LocalDate.parse(from)
        val toDate = LocalDate.parse(to)
        return timesheetService.list(auth.name, fromDate, toDate)
    }

    @PostMapping("/note")
    fun updateNote(auth: Authentication, @RequestBody body: Map<String, String>): TimesheetEntry {
        val note = body["note"] ?: ""
        val entry = timesheetService.updateNote(auth.name, note)
        broadcast("note", entry)
        return entry
    }

    @PostMapping("/note/saveBeacon")
    fun saveBeacon(auth: Authentication, @RequestParam note: String?): TimesheetEntry {
        val safe = note ?: ""
        val entry = timesheetService.updateNote(auth.name, safe)
        // beaconではSSE broadcastは省略しても良いが、即時反映のため送信
        broadcast("note", entry)
        return entry
    }

    @GetMapping(path = ["/stream"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(): SseEmitter {
        val emitter = SseEmitter(0L)
        emitters.add(emitter)
        emitter.onCompletion { emitters.remove(emitter) }
        emitter.onTimeout { emitters.remove(emitter) }
        // 心拍送信: 30秒毎
        val heartbeat = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        val future = heartbeat.scheduleAtFixedRate({
            try {
                emitter.send(SseEmitter.event().name("heartbeat").data("ping"))
            } catch (_: Exception) {
                // 切断を検知したら停止
                heartbeat.shutdownNow()
            }
        }, 30, 30, java.util.concurrent.TimeUnit.SECONDS)
        emitter.onCompletion { future.cancel(true); heartbeat.shutdownNow() }
        emitter.onTimeout { future.cancel(true); heartbeat.shutdownNow() }
        return emitter
    }

    private fun broadcast(event: String, data: Any) {
        val dead = mutableListOf<SseEmitter>()
        emitters.forEach { em ->
            try {
                em.send(SseEmitter.event().name(event).data(data))
            } catch (_: Exception) {
                dead.add(em)
            }
        }
        emitters.removeAll(dead)
    }

    @ExceptionHandler(TimesheetConflictException::class)
    fun handleConflict(ex: TimesheetConflictException): ResponseEntity<Map<String, Any>> {
        val body = mapOf<String, Any>(
            "code" to "CONFLICT",
            "message" to (ex.message ?: "")
        )
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body)
    }

    @ExceptionHandler(TimesheetNotFoundException::class)
    fun handleNotFound(ex: TimesheetNotFoundException): ResponseEntity<Map<String, Any>> {
        val body = mapOf<String, Any>(
            "code" to "NOT_FOUND",
            "message" to (ex.message ?: "")
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body)
    }

    @PostMapping("/batch")
    fun batchSave(auth: Authentication, @RequestBody body: Map<String, List<Map<String, String?>>>): Map<String, Any> {
        val entries = body["entries"] ?: emptyList()
        var saved = 0

        entries.forEach { entry ->
            val workDateStr = entry["workDate"] ?: return@forEach
            val startTimeStr = entry["startTime"]
            val endTimeStr = entry["endTime"]

            try {
                val workDate = LocalDate.parse(workDateStr)
                val startTime = startTimeStr?.let { java.time.LocalTime.parse(it) }
                val endTime = endTimeStr?.let { java.time.LocalTime.parse(it) }

                timesheetService.saveOrUpdate(auth.name, workDate, startTime, endTime)
                saved++
            } catch (_: Exception) {
                // スキップして次へ
            }
        }

        return mapOf("saved" to saved, "total" to entries.size)
    }
}
