package com.healthdiary.service;

import com.healthdiary.entity.HealthLog;
import com.healthdiary.repository.HealthLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Inserts a small, hand-labeled "seed" dataset on first startup so the
 * decision tree has training data (Sleep Hours / Steps / Mood Score -> Risk
 * Level) to learn from immediately. The data intentionally contains some
 * noise on steps/mood so that sleep_hours comes out as the feature with the
 * highest Information Gain -- a more realistic demo of C4.5-style splitting
 * than a perfectly separable toy set.
 */
@Component
public class SeedDataLoader implements ApplicationRunner {

    private final HealthLogRepository repository;
    private final HealthLogService healthLogService;

    @Value("${healthdiary.seed.enabled:true}")
    private boolean seedEnabled;

    public SeedDataLoader(HealthLogRepository repository, HealthLogService healthLogService) {
        this.repository = repository;
        this.healthLogService = healthLogService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled) return;
        if (repository.countByIsSeedDataTrue() > 0) {
            healthLogService.retrain();
            return;
        }

        LocalDate base = LocalDate.now().minusDays(60);
        Object[][] seed = {
                {8.0, 9000, 8, "Low"}, {7.5, 3000, 9, "Low"}, {7.0, 7800, 4, "Low"},
                {8.2, 10000, 8, "Low"}, {6.8, 7200, 7, "Low"}, {7.8, 2500, 9, "Low"},
                {7.2, 8800, 3, "Low"}, {7.6, 9200, 8, "Low"},
                {6.5, 6000, 6, "Medium"}, {6.0, 5500, 5, "Medium"}, {5.5, 8000, 6, "Medium"},
                {6.2, 5200, 9, "Medium"}, {5.8, 6200, 6, "Medium"}, {6.4, 4500, 5, "Medium"},
                {5.0, 3000, 4, "Medium"}, {5.2, 3500, 5, "Medium"}, {6.6, 6500, 6, "Medium"},
                {4.0, 2000, 3, "High"}, {3.5, 9000, 2, "High"}, {4.5, 2500, 8, "High"},
                {3.0, 1000, 2, "High"}, {4.2, 1800, 4, "High"}, {3.8, 2200, 3, "High"},
                {4.8, 2700, 3, "High"},
        };

        for (int i = 0; i < seed.length; i++) {
            HealthLog log = new HealthLog();
            log.setLogDate(base.plusDays(i));
            log.setSleepHours((Double) seed[i][0]);
            log.setSteps((Integer) seed[i][1]);
            log.setMoodScore((Integer) seed[i][2]);
            log.setRiskLevel((String) seed[i][3]);
            log.setIsSeedData(true);
            repository.save(log);
        }

        healthLogService.retrain();
    }
}
