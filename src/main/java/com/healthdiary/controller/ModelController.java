package com.healthdiary.controller;

import com.healthdiary.dto.SeedDataRequest;
import com.healthdiary.entity.HealthLog;
import com.healthdiary.service.DecisionTreeService;
import com.healthdiary.service.HealthLogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/model")
public class ModelController {

    private final HealthLogService healthLogService;
    private final DecisionTreeService decisionTreeService;

    public ModelController(HealthLogService healthLogService, DecisionTreeService decisionTreeService) {
        this.healthLogService = healthLogService;
        this.decisionTreeService = decisionTreeService;
    }

    /** Full tree structure + entropy + information gain per feature, for the Model Insights page. */
    @GetMapping("/tree")
    public Map<String, Object> getTree() {
        return decisionTreeService.getTreeAsJson();
    }

    /** Recompute the tree from the current seed/training data. */
    @PostMapping("/train")
    public DecisionTreeService.TrainingSummary train() {
        return healthLogService.retrain();
    }

    @GetMapping("/seed")
    public List<HealthLog> listSeedData() {
        return healthLogService.listSeedData();
    }

    @PostMapping("/seed")
    public ResponseEntity<HealthLog> addSeedData(@Valid @RequestBody SeedDataRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(healthLogService.addSeedData(request));
    }

    @DeleteMapping("/seed/{id}")
    public ResponseEntity<Void> deleteSeedData(@PathVariable Long id) {
        healthLogService.deleteSeedData(id);
        return ResponseEntity.noContent().build();
    }
}
