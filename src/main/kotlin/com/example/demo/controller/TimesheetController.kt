package com.example.demo.controller

import com.example.demo.model.TimesheetEntry
import com.example.demo.service.TimesheetService
import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@RestController
@RequestMapping("/timesheet/api")
class TimesheetController(
    private val timesheetService: TimesheetService,
    private val summaryService: com.example.demo.service.TimesheetSummaryService
) {
    // ユーザ毎に複数のエミッターを保持
    private val emitters = ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>>()

    @PostMapping("/clock-in")
    fun clockIn(auth: Authentication): TimesheetEntry {
        val entry = timesheetService.clockIn(auth.name)
        broadcast("clock-in", entry, auth.name)
        return entry
    }

    @PostMapping("/clock-out")
    fun clockOut(auth: Authentication): TimesheetEntry {
        val entry = timesheetService.clockOut(auth.name, LocalTime.now())
        broadcast("clock-out", entry, auth.name)
        return entry
    }

    @GetMapping("/today")
    fun today(auth: Authentication): TimesheetEntry? = timesheetService.getToday(auth.name)

    @GetMapping
    fun list(
        auth: Authentication,
        @RequestParam from: String,
        @RequestParam(required = false) to: String?
    ): List<TimesheetEntry> {
        val fromDate = LocalDate.parse(from)
        val toDate = LocalDate.parse(to ?: from)
        return timesheetService.list(auth.name, fromDate, toDate)
    }

    @PostMapping("/note")
    fun updateNote(auth: Authentication, @RequestBody body: Map<String, String>): TimesheetEntry {
        val note = body["note"] ?: ""
        val entry = timesheetService.updateNote(auth.name, note)
        broadcast("note", entry, auth.name)
        return entry
    }

    @PostMapping("/note/saveBeacon")
    fun saveBeacon(auth: Authentication, @RequestParam note: String?): TimesheetEntry {
        val safe = note ?: ""
        val entry = timesheetService.updateNote(auth.name, safe)
        // beaconではSSE broadcastは省略しても良いが、即時反映のため送信
        broadcast("note", entry, auth.name)
        return entry
    }

    @PostMapping("/break")
    fun updateBreak(auth: Authentication, @RequestBody body: Map<String, Int>): TimesheetEntry {
        val minutes = body["minutes"] ?: 0
        val today = LocalDate.now()
        val existing = timesheetService.getToday(auth.name)
        val updated = timesheetService.saveOrUpdate(
            auth.name,
            today,
            existing?.startTime,
            existing?.endTime,
            minutes,
            false,
            existing?.holidayWork ?: false
        )
        broadcast("break", updated, auth.name)
        return updated
    }

    @GetMapping(path = ["/stream"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(auth: Authentication): SseEmitter {
        val emitter = SseEmitter(0L)
        val list = emitters.computeIfAbsent(auth.name) { CopyOnWriteArrayList() }
        list.add(emitter)
        emitter.onCompletion { list.remove(emitter) }
        emitter.onTimeout { list.remove(emitter) }
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

    private fun broadcast(event: String, data: Any, userName: String) {
        val list = emitters[userName] ?: return
        val dead = mutableListOf<SseEmitter>()
        list.forEach { em ->
            try {
                em.send(SseEmitter.event().name(event).data(data))
            } catch (_: Exception) {
                dead.add(em)
            }
        }
        list.removeAll(dead)
    }

    @PostMapping("/batch")
    fun batchSave(auth: Authentication, @RequestBody body: Map<String, List<Map<String, String?>>>): Map<String, Any> {
        val entries = body["entries"] ?: emptyList()
        var saved = 0
        val failures = mutableListOf<Map<String, Any?>>()

        entries.forEach { entry ->
            val workDateStr = entry["workDate"] ?: return@forEach
            val startTimeStr = entry["startTime"]
            val endTimeStr = entry["endTime"]
            val breakStr = entry["breakMinutes"]
            val holidayStr = entry["holidayWork"]
            try {
                val workDate = LocalDate.parse(workDateStr)
                val startTime =
                    startTimeStr?.let { if (it.isNotBlank()) LocalTime.parse(it).withSecond(0).withNano(0) else null }
                val endTime =
                    endTimeStr?.let { if (it.isNotBlank()) LocalTime.parse(it).withSecond(0).withNano(0) else null }
                val breakMinutes = breakStr?.let { if (it.isNotBlank()) it.toIntOrNull() else null }
                val holidayWork = holidayStr?.equals("true", ignoreCase = true) ?: false

                // detect presence of keys to allow explicit null -> clear behavior
                val startProvided = entry.containsKey("startTime")
                val endProvided = entry.containsKey("endTime")
                val breakProvided = entry.containsKey("breakMinutes")
                val savedEntry = timesheetService.saveOrUpdateWithFlags(
                    auth.name,
                    workDate,
                    startProvided,
                    startTime,
                    endProvided,
                    endTime,
                    breakProvided,
                    breakMinutes,
                    false,
                    holidayWork
                )
                // 個別保存なら user 宛に通知
                broadcast("timesheet-updated", savedEntry, auth.name)
                saved++
            } catch (ex: Exception) {
                failures += mapOf(
                    "workDate" to workDateStr,
                    "error" to (ex.message ?: "parse/save error"),
                    "startTime" to startTimeStr,
                    "endTime" to endTimeStr,
                    "breakMinutes" to breakStr
                )
            }
        }

        return mapOf(
            "saved" to saved,
            "total" to entries.size,
            "failed" to failures.size,
            "failures" to failures
        )
    }

    @GetMapping("/summary")
    fun summary(
        auth: Authentication,
        @RequestParam month: String
    ): com.example.demo.service.TimesheetSummaryService.Summary {
        val ym = java.time.YearMonth.parse(month)
        return summaryService.summarize(auth.name, ym)
    }

    @PostMapping("/entry")
    fun saveEntry(auth: Authentication, @RequestBody body: Map<String, String?>): Map<String, Any> {
        val workDateStr = body["workDate"] ?: return mapOf("success" to false, "message" to "workDate required")
        try {
            val workDate = LocalDate.parse(workDateStr)
            val startTime =
                body["startTime"]?.takeIf { it.isNotBlank() }?.let { LocalTime.parse(it).withSecond(0).withNano(0) }
            val endTime =
                body["endTime"]?.takeIf { it.isNotBlank() }?.let { LocalTime.parse(it).withSecond(0).withNano(0) }
            val breakMinutes = body["breakMinutes"]?.takeIf { it.isNotBlank() }?.toIntOrNull()
            val force = body["force"]?.toBoolean() ?: false
            val holidayWork = body["holidayWork"]?.toBoolean() ?: false
            val note = body["note"]?.takeIf { it.isNotBlank() }
            val noteProvided = body.containsKey("note")
            val workLocation = body["workLocation"]?.takeIf { it.isNotBlank() }
            val irregularWorkType = body["irregularWorkType"]?.takeIf { it.isNotBlank() }
            val irregularWorkDesc = body["irregularWorkDesc"]?.takeIf { it.isNotBlank() }
            val lateTime = body["lateTime"]?.takeIf { it.isNotBlank() }
            val lateDesc = body["lateDesc"]?.takeIf { it.isNotBlank() }
            val earlyTime = body["earlyTime"]?.takeIf { it.isNotBlank() }
            val earlyDesc = body["earlyDesc"]?.takeIf { it.isNotBlank() }
            val paidLeave = body["paidLeave"]?.takeIf { it.isNotBlank() }

            // クリアフラグ: キーが存在し、値が空またはnullの場合はクリア
            val clearIrregular = body.containsKey("irregularWorkType") && body["irregularWorkType"].isNullOrBlank()
            val clearLate = body.containsKey("lateTime") && body["lateTime"].isNullOrBlank()
            val clearEarly = body.containsKey("earlyTime") && body["earlyTime"].isNullOrBlank()

            // detect presence of keys to allow explicit null -> clear behavior
            val startProvided = body.containsKey("startTime")
            val endProvided = body.containsKey("endTime")
            val breakProvided = body.containsKey("breakMinutes")
            val saved = timesheetService.saveOrUpdateWithFlags(
                auth.name,
                workDate,
                startProvided,
                startTime,
                endProvided,
                endTime,
                breakProvided,
                breakMinutes,
                force,
                holidayWork,
                noteProvided,
                note,
                workLocation,
                irregularWorkType,
                irregularWorkDesc,
                lateTime,
                lateDesc,
                earlyTime,
                earlyDesc,
                paidLeave,
                clearIrregular,
                clearLate,
                clearEarly
            )
            // ブロードキャストして（同一ユーザの）他クライアントへ反映
            broadcast("timesheet-updated", saved, auth.name)
            return mapOf("success" to true, "entry" to saved)
        } catch (ex: Exception) {
            return mapOf("success" to false, "message" to (ex.message ?: "save error"))
        }
    }

    @PostMapping("/add-note")
    fun addNote(auth: Authentication, @RequestBody body: Map<String, String>): TimesheetEntry {
        val note = body["note"] ?: throw IllegalArgumentException("Note is required")
        val entry = timesheetService.addNoteToEntry(auth.name, note)
        broadcast("add-note", entry, auth.name)
        return entry
    }
}
