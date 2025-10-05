package com.example.demo

import com.example.demo.model.Path
import com.example.demo.service.PathService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/paths")
class ApiPathController(
    private val pathService: PathService
) {
    @GetMapping("/all")
    fun allActive(): ResponseEntity<List<Path>> =
        ResponseEntity.ok(pathService.getAllActive())

    @GetMapping("/allIncludingDeleted")
    fun allIncludingDeleted(): ResponseEntity<List<Path>> =
        ResponseEntity.ok(pathService.getAllIncludingDeleted())

    @PostMapping
    fun create(@Valid @RequestBody path: Path): ResponseEntity<Path> =
        ResponseEntity.status(HttpStatus.CREATED).body(path.also { pathService.insert(it) })

    @PutMapping
    fun update(@Valid @RequestBody path: Path): ResponseEntity<Path> {
        require(path.id != null) { "IDは更新時に必須です" }
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
