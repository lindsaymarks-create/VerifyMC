package team.kitemc.verifymc.service;

import org.bukkit.plugin.Plugin;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public abstract class OpenAICompatibleScoringProvider implements EssayScoringService {
    protected final Plugin plugin;
    private final LlmScoringConfig config;
    private final HttpClient client;

    protected OpenAICompatibleScoringProvider(Plugin plugin, LlmScoringConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.getTimeoutMs()))
            .build();
    }

    @Override
    public EssayScoringResult score(EssayScoringRequest request) {
        if (!config.isReady()) {
            return new EssayScoringResult(0, "LLM config incomplete, requires manual review", 0.0D, true);
        }

        int attempts = Math.max(0, config.getRetry()) + 1;
        for (int i = 1; i <= attempts; i++) {
            try {
                String content = callModel(request);
                return parseResult(content, request.getMaxScore());
            } catch (Exception e) {
                if (i == attempts) {
                    plugin.getLogger().warning("[VerifyMC] LLM scoring failed after retries: " + e.getMessage());
                    return new EssayScoringResult(0, "LLM scoring unavailable, requires manual review", 0.0D, true);
                }
                plugin.getLogger().warning("[VerifyMC] LLM scoring attempt " + i + " failed: " + e.getMessage());
            }
        }
        return new EssayScoringResult(0, "LLM scoring unavailable, requires manual review", 0.0D, true);
    }

    private String callModel(EssayScoringRequest request) throws IOException, InterruptedException {
        JSONObject payload = new JSONObject();
        payload.put("model", config.getModel());
        payload.put("temperature", 0.0D);

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", config.getSystemPrompt()));
        messages.put(new JSONObject().put("role", "user").put("content", buildUserPrompt(request)));
        payload.put("messages", messages);

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(normalizeChatCompletionsUrl(config.getApiBase())))
            .header("Authorization", "Bearer " + config.getApiKey())
            .header("Content-Type", "application/json")
            .timeout(Duration.ofMillis(config.getTimeoutMs()))
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build();

        HttpResponse<String> resp = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("HTTP " + resp.statusCode() + ": " + resp.body());
        }

        JSONObject json = new JSONObject(resp.body());
        JSONArray choices = json.optJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IOException("No choices returned by model");
        }
        JSONObject message = choices.getJSONObject(0).optJSONObject("message");
        if (message == null) {
            throw new IOException("Missing message in model response");
        }
        String content = message.optString("content", "").trim();
        if (content.isEmpty()) {
            throw new IOException("Empty model response");
        }
        return content;
    }

    private String buildUserPrompt(EssayScoringRequest request) {
        return "Question ID: " + request.getQuestionId() + "\n"
            + "Question: " + request.getQuestion() + "\n"
            + "Candidate Answer: " + request.getAnswer() + "\n"
            + "Scoring Rule: " + request.getScoringRule() + "\n"
            + "Maximum Score: " + request.getMaxScore() + "\n"
            + "Output format requirement: " + config.getScoreFormat() + "\n"
            + "Only return JSON. Do not include markdown or extra commentary.";
    }

    private EssayScoringResult parseResult(String rawContent, int maxScore) {
        JSONObject resultJson = new JSONObject(extractJsonObject(rawContent));
        int score = Math.max(0, Math.min(maxScore, resultJson.optInt("score", 0)));
        String reason = resultJson.optString("reason", "No reason provided");
        double confidence = resultJson.optDouble("confidence", 0.0D);

        return new EssayScoringResult(score, reason, confidence, false);
    }

    private String extractJsonObject(String rawContent) {
        String cleaned = rawContent != null ? rawContent.trim() : "";
        if (cleaned.startsWith("```") && cleaned.endsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }

        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }
        return cleaned;
    }

    protected String normalizeChatCompletionsUrl(String base) {
        String url = base != null ? base.trim() : "";
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (!url.endsWith("/chat/completions")) {
            url = url + "/chat/completions";
        }
        return url;
    }

    public static class LlmScoringConfig {
        private final String apiBase;
        private final String apiKey;
        private final String model;
        private final int timeoutMs;
        private final int retry;
        private final String systemPrompt;
        private final String scoreFormat;

        public LlmScoringConfig(String apiBase, String apiKey, String model, int timeoutMs, int retry, String systemPrompt, String scoreFormat) {
            this.apiBase = apiBase != null ? apiBase.trim() : "";
            this.apiKey = apiKey != null ? apiKey.trim() : "";
            this.model = model != null ? model.trim() : "";
            this.timeoutMs = Math.max(timeoutMs, 1000);
            this.retry = Math.max(retry, 0);
            this.systemPrompt = systemPrompt != null ? systemPrompt : "";
            this.scoreFormat = scoreFormat != null ? scoreFormat : "";
        }

        public String getApiBase() { return apiBase; }
        public String getApiKey() { return apiKey; }
        public String getModel() { return model; }
        public int getTimeoutMs() { return timeoutMs; }
        public int getRetry() { return retry; }
        public String getSystemPrompt() { return systemPrompt; }
        public String getScoreFormat() { return scoreFormat; }

        public boolean isReady() {
            return !apiBase.isEmpty() && !apiKey.isEmpty() && !model.isEmpty();
        }
    }
}
