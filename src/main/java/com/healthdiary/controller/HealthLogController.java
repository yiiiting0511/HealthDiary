package com.healthdiary.controller;

import com.healthdiary.dto.HealthLogRequest;
import com.healthdiary.entity.HealthLog;
import com.healthdiary.service.HealthLogService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/logs")
public class HealthLogController {

    private final HealthLogService service;

    public HealthLogController(HealthLogService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<HealthLog> create(@Valid @RequestBody HealthLogRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createLog(request));
    }

    @GetMapping
    public List<HealthLog> list(@RequestParam(defaultValue = "desc") String sort) {
        return service.listNonSeedLogs(sort);
    }

    @GetMapping("/trends")
    public List<HealthLog> trends(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.trendData(from, to);
    }

    @GetMapping("/{id}")
    public HealthLog get(@PathVariable Long id) {
        return service.getLog(id);
    }

    @PutMapping("/{id}")
    public HealthLog update(@PathVariable Long id, @RequestBody HealthLogRequest request) {
        return service.updateLog(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteLog(id);
        return ResponseEntity.noContent().build();
    }
}
