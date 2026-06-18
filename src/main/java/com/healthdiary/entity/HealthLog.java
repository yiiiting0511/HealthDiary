package com.healthdiary.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Maps to the health_logs table.
 *
 * Columns:
 *   id            INTEGER PRIMARY KEY AUTOINCREMENT
 *   log_date      DATE NOT NULL
 *   sleep_hours   REAL NOT NULL
 *   steps         INTEGER NOT NULL
 *   mood_score    INTEGER NOT NULL
 *   risk_level    TEXT (nullable) -- produced by the decision tree
 *   is_seed_data  BOOLEAN NOT NULL DEFAULT FALSE -- marks rows used as labeled
 *                 training data for the decision tree (added during DB design,
 *                 see step 3 of the project design)
 *   created_at    TIMESTAMP -- bookkeeping, not part of the original spec table
 */
@Entity
@Table(name = "health_logs")
public class HealthLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Column(name = "sleep_hours", nullable = false)
    private Double sleepHours;

    @Column(name = "steps", nullable = false)
    private Integer steps;

    @Column(name = "mood_score", nullable = false)
    private Integer moodScore;

    @Column(name = "risk_level")
    private String riskLevel;

    @Column(name = "is_seed_data", nullable = false)
    private Boolean isSeedData = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public HealthLog() {
    }

    public HealthLog(LocalDate logDate, Double sleepHours, Integer steps, Integer moodScore) {
        this.logDate = logDate;
        this.sleepHours = sleepHours;
        this.steps = steps;
        this.moodScore = moodScore;
        this.isSeedData = false;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (isSeedData == null) {
            isSeedData = false;
        }
    }

    // ---- Getters / setters ----

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getLogDate() {
        return logDate;
    }

    public void setLogDate(LocalDate logDate) {
        this.logDate = logDate;
    }

    public Double getSleepHours() {
        return sleepHours;
    }

    public void setSleepHours(Double sleepHours) {
        this.sleepHours = sleepHours;
    }

    public Integer getSteps() {
        return steps;
    }

    public void setSteps(Integer steps) {
        this.steps = steps;
    }

    public Integer getMoodScore() {
        return moodScore;
    }

    public void setMoodScore(Integer moodScore) {
        this.moodScore = moodScore;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Boolean getIsSeedData() {
        return isSeedData;
    }

    public void setIsSeedData(Boolean isSeedData) {
        this.isSeedData = isSeedData;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
