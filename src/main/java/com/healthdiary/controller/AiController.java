package com.healthdiary.controller;

import com.healthdiary.entity.HealthLog;
import com.healthdiary.service.AiInsightService;
import com.healthdiary.service.HealthLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Final stage of the data flow: Frontend -> API -> Database -> AI Analysis. */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final HealthLogService healthLogService;
    private final AiInsightService aiInsightService;

    public AiController(HealthLogService healthLogService, AiInsightService aiInsightService) {
        this.healthLogService = healthLogService;
        this.aiInsightService = aiInsightService;
    }

    @GetMapping("/insight/{logId}")
    public Map<String, String> getInsight(@PathVariable Long logId) {
        HealthLog log = healthLogService.getLog(logId);
        String insight = aiInsightService.generateInsight(log);
        return Map.of("logId", String.valueOf(logId), "riskLevel", log.getRiskLevel(), "insight", insight);
    }
}
