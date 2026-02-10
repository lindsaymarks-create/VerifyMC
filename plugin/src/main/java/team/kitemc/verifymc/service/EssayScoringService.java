package team.kitemc.verifymc.service;

public interface EssayScoringService {
    EssayScoringResult score(EssayScoringRequest request);

    class EssayScoringRequest {
        private final int questionId;
        private final String question;
        private final String answer;
        private final String scoringRule;
        private final int maxScore;

        public EssayScoringRequest(int questionId, String question, String answer, String scoringRule, int maxScore) {
            this.questionId = questionId;
            this.question = clean(question, 2000);
            this.answer = clean(answer, 2000);
            this.scoringRule = clean(scoringRule, 2000);
            this.maxScore = Math.max(maxScore, 0);
        }

        public int getQuestionId() {
            return questionId;
        }

        public String getQuestion() {
            return question;
        }

        public String getAnswer() {
            return answer;
        }

        public String getScoringRule() {
            return scoringRule;
        }

        public int getMaxScore() {
            return maxScore;
        }

        private static String clean(String value, int maxLen) {
            String normalized = value != null ? value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ").trim() : "";
            if (normalized.length() > maxLen) {
                return normalized.substring(0, maxLen);
            }
            return normalized;
        }
    }

    class EssayScoringResult {
        private final int score;
        private final String reason;
        private final double confidence;
        private final boolean manualReview;
        private final String provider;
        private final String model;
        private final String requestId;
        private final long latencyMs;
        private final int retryCount;

        public EssayScoringResult(int score, String reason, double confidence, boolean manualReview) {
            this(score, reason, confidence, manualReview, "", "", "", 0L, 0);
        }

        public EssayScoringResult(int score, String reason, double confidence, boolean manualReview,
                                  String provider, String model, String requestId, long latencyMs, int retryCount) {
            this.score = score;
            this.reason = reason != null ? reason : "";
            this.confidence = Math.max(0.0D, Math.min(1.0D, confidence));
            this.manualReview = manualReview;
            this.provider = provider != null ? provider : "";
            this.model = model != null ? model : "";
            this.requestId = requestId != null ? requestId : "";
            this.latencyMs = Math.max(0L, latencyMs);
            this.retryCount = Math.max(0, retryCount);
        }

        public int getScore() {
            return score;
        }

        public String getReason() {
            return reason;
        }

        public double getConfidence() {
            return confidence;
        }

        public boolean isManualReview() {
            return manualReview;
        }

        public String getProvider() { return provider; }

        public String getModel() { return model; }

        public String getRequestId() { return requestId; }

        public long getLatencyMs() { return latencyMs; }

        public int getRetryCount() { return retryCount; }
    }
}
