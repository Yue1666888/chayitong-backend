package com.chayitong.recommendation;

import com.chayitong.tea.TeaModels.TeaDetail;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class RecommendationModels {
    private RecommendationModels() {
    }

    public record QuizOption(String value, String label) {
    }

    public record QuizQuestion(String key, String title, List<QuizOption> options) {
    }

    public record QuizResponse(String quizVersion, List<QuizQuestion> questions) {
    }

    public record RecommendationRequest(String quizVersion, Map<String, String> answers) {
    }

    public record RecommendationItem(TeaDetail tea, Integer score, Integer traitScore,
            Integer matchPercent, List<String> reasons) {
    }

    public record RecommendationResult(String resultId, String quizVersion, Map<String, String> answers,
            Instant createdAt, List<RecommendationItem> recommendations) {
    }

    record Score(int score, int traitScore, int matchPercent, List<String> reasons) {
    }
}
