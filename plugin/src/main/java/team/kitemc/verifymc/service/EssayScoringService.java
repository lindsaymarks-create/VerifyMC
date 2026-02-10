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
            this.question = question != null ? question : "";
            this.answer = answer != null ? answer : "";
            this.scoringRule = scoringRule != null ? scoringRule : "";
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
    }

    class EssayScoringResult {
        private final int score;
        private final String reason;
        private final double confidence;
        private final boolean manualReview;

        public EssayScoringResult(int score, String reason, double confidence, boolean manualReview) {
            this.score = score;
            this.reason = reason != null ? reason : "";
            this.confidence = Math.max(0.0D, Math.min(1.0D, confidence));
            this.manualReview = manualReview;
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
    }
}
