package com.example.demo

import com.example.demo.model.Path
import com.example.demo.service.PathService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/paths")
class ApiPathController(
    private val pathService: PathService
) {
    @GetMapping("/all")
    fun allActive(): List<Path> = pathService.getAllActive()

    @GetMapping("/allIncludingDeleted")
    fun allIncludingDeleted(): List<Path> = pathService.getAllIncludingDeleted()

    @PostMapping
    fun create(@Valid @RequestBody path: Path): ResponseEntity<Path> {
        pathService.insert(path)
        return ResponseEntity.ok(path)
    }

    @PutMapping
    fun update(@Valid @RequestBody path: Path): ResponseEntity<Path> {
        if (path.id == null) return ResponseEntity.badRequest().build()
        pathService.update(path)
        return ResponseEntity.ok(path)
    }

    @DeleteMapping("/{id}")
    fun logicalDelete(@PathVariable id: Long): ResponseEntity<Void> {
        pathService.logicalDelete(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/restore")
    fun restore(@PathVariable id: Long): ResponseEntity<Void> {
        pathService.restore(id)
        return ResponseEntity.noContent().build()
    }
}

