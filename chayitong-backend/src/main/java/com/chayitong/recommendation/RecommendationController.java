package com.chayitong.recommendation;

import com.chayitong.common.api.CommonResponse;
import com.chayitong.recommendation.RecommendationModels.QuizResponse;
import com.chayitong.recommendation.RecommendationModels.RecommendationRequest;
import com.chayitong.recommendation.RecommendationModels.RecommendationResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecommendationController {

    private final QuizService quizService;
    private final RecommendationService recommendationService;

    public RecommendationController(QuizService quizService, RecommendationService recommendationService) {
        this.quizService = quizService;
        this.recommendationService = recommendationService;
    }

    @GetMapping("/api/quiz")
    public CommonResponse<QuizResponse> quiz() {
        return CommonResponse.success(quizService.currentQuiz());
    }

    @PostMapping("/api/recommendations")
    public CommonResponse<RecommendationResult> create(@RequestBody RecommendationRequest request) {
        return CommonResponse.success(recommendationService.create(request));
    }

    @GetMapping("/api/recommendations/{resultId}")
    public CommonResponse<RecommendationResult> get(@PathVariable String resultId) {
        return CommonResponse.success(recommendationService.get(resultId));
    }
}
