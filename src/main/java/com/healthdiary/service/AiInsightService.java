package com.healthdiary.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthdiary.entity.HealthLog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Calls the Google Gemini API to turn the decision tree's risk_level
 * output into a short, human-readable piece of health advice. This is the
 * "AI Analysis" stage at the end of the Frontend -> API -> Database -> AI
 * Analysis data flow.
 *
 * If no GEMINI_API_KEY is configured (e.g. running locally without one),
 * falls back to a deterministic rule-based message instead of failing the
 * request.
 */
@Service
public class AiInsightService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    @Value("${gemini.api.model}")
    private String model;

    private static final String SYSTEM_PROMPT =
            "你是一位簡潔、務實的健康顧問，回覆限制在兩句繁體中文以內。";

    public String generateInsight(HealthLog log) {
        if (apiKey == null || apiKey.isBlank()) {
            return fallbackInsight(log);
        }
        try {
            String prompt = buildPrompt(log);

            Map<String, Object> body = Map.of(
                    "systemInstruction", Map.of(
                            "parts", List.of(Map.of("text", SYSTEM_PROMPT))
                    ),
                    "contents", List.of(
                            Map.of(
                                    "role", "user",
                                    "parts", List.of(Map.of("text", prompt))
                            )
                    ),
                    "generationConfig", Map.of(
                            "temperature", 0.5,
                            "maxOutputTokens", 120
                    )
            );

            String endpoint = apiUrl + "/" + model + ":generateContent?key=" + apiKey;

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return fallbackInsight(log);
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode content = root.path("candidates").path(0).path("content")
                    .path("parts").path(0).path("text");
            if (content.isMissingNode() || content.asText().isBlank()) {
                return fallbackInsight(log);
            }
            return content.asText().trim();
        } catch (Exception e) {
            return fallbackInsight(log);
        }
    }

    private String buildPrompt(HealthLog log) {
        return String.format(
                "使用者今日紀錄：睡眠 %.1f 小時、步數 %d 步、心情分數 %d/10，系統評估風險等級為 %s。" +
                        "請給一句具體可行的健康建議。",
                log.getSleepHours(), log.getSteps(), log.getMoodScore(), log.getRiskLevel()
        );
    }

    private String fallbackInsight(HealthLog log) {
        String risk = log.getRiskLevel() == null ? "Medium" : log.getRiskLevel();
        return switch (risk) {
            case "High" -> "睡眠與活動量偏低，建議今天提早休息並安排短時間散步，必要時諮詢醫療專業人員。";
            case "Medium" -> "狀態中等，建議維持規律睡眠並增加一些步行量，注意觀察心情變化。";
            default -> "目前狀態良好，請持續維持規律的睡眠、運動與作息習慣。";
        };
    }
}
