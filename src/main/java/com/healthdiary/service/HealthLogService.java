package com.healthdiary.service;

import com.healthdiary.dto.HealthLogRequest;
import com.healthdiary.dto.SeedDataRequest;
import com.healthdiary.entity.HealthLog;
import com.healthdiary.repository.HealthLogRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class HealthLogService {

    private final HealthLogRepository repository;
    private final DecisionTreeService decisionTreeService;

    public HealthLogService(HealthLogRepository repository, DecisionTreeService decisionTreeService) {
        this.repository = repository;
        this.decisionTreeService = decisionTreeService;
    }

    /** Train the tree from whatever seed data already exists as soon as the app is ready. */
    @PostConstruct
    public void initTreeFromExistingSeedData() {
        List<HealthLog> seeds = repository.findByIsSeedDataTrue();
        if (!seeds.isEmpty()) {
            decisionTreeService.train(seeds);
        }
    }

    // ---- Logs (history / entry) ----

    public HealthLog createLog(HealthLogRequest request) {
        HealthLog log = new HealthLog();
        log.setLogDate(request.getLogDate() != null ? request.getLogDate() : LocalDate.now());
        log.setSleepHours(request.getSleepHours());
        log.setSteps(request.getSteps());
        log.setMoodScore(request.getMoodScore());
        log.setIsSeedData(false);
        log.setRiskLevel(decisionTreeService.predict(request.getSleepHours(), request.getSteps(), request.getMoodScore()));
        return repository.save(log);
    }

    public List<HealthLog> listLogs(String sort) {
        if ("asc".equalsIgnoreCase(sort)) {
            return repository.findAllByOrderByLogDateAsc();
        }
        return repository.findAllByOrderByLogDateDesc();
    }

    /** History page data: real user entries only, excluding seed/training rows. */
    public List<HealthLog> listNonSeedLogs(String sort) {
        List<HealthLog> logs = repository.findByIsSeedDataFalseOrderByLogDateDesc();
        if ("asc".equalsIgnoreCase(sort)) {
            List<HealthLog> reversed = new java.util.ArrayList<>(logs);
            java.util.Collections.reverse(reversed);
            return reversed;
        }
        return logs;
    }

    public List<HealthLog> trendData(LocalDate from, LocalDate to) {
        if (from != null && to != null) {
            return repository.findByDateRange(from, to);
        }
        return repository.findAllByOrderByLogDateAsc();
    }

    public HealthLog getLog(Long id) {
        return repository.findById(id).orElseThrow(() -> new NoSuchElementException("Log not found: " + id));
    }

    public HealthLog updateLog(Long id, HealthLogRequest request) {
        HealthLog log = getLog(id);
        if (request.getLogDate() != null) log.setLogDate(request.getLogDate());
        if (request.getSleepHours() != null) log.setSleepHours(request.getSleepHours());
        if (request.getSteps() != null) log.setSteps(request.getSteps());
        if (request.getMoodScore() != null) log.setMoodScore(request.getMoodScore());
        // Re-run prediction since the underlying metrics changed.
        log.setRiskLevel(decisionTreeService.predict(log.getSleepHours(), log.getSteps(), log.getMoodScore()));
        return repository.save(log);
    }

    public void deleteLog(Long id) {
        repository.deleteById(id);
    }

    // ---- Seed / training data ----

    public List<HealthLog> listSeedData() {
        return repository.findByIsSeedDataTrue();
    }

    public HealthLog addSeedData(SeedDataRequest request) {
        HealthLog log = new HealthLog();
        log.setLogDate(request.getLogDate() != null ? request.getLogDate() : LocalDate.now());
        log.setSleepHours(request.getSleepHours());
        log.setSteps(request.getSteps());
        log.setMoodScore(request.getMoodScore());
        log.setRiskLevel(request.getRiskLevel());
        log.setIsSeedData(true);
        HealthLog saved = repository.save(log);
        retrain();
        return saved;
    }

    public void deleteSeedData(Long id) {
        repository.deleteById(id);
        retrain();
    }

    public DecisionTreeService.TrainingSummary retrain() {
        List<HealthLog> seeds = repository.findByIsSeedDataTrue();
        return decisionTreeService.train(seeds);
    }
}
