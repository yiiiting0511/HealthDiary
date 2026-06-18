package com.healthdiary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;

@SpringBootApplication
public class HealthDiaryApplication {
    public static void main(String[] args) {
        String dbPath = System.getenv().getOrDefault("DB_PATH", "./data/health_diary.db");
        File parent = new File(dbPath).getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        SpringApplication.run(HealthDiaryApplication.class, args);
    }
}
