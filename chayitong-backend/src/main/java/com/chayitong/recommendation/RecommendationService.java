package com.chayitong.recommendation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.exceptions.BusinessException;
import com.chayitong.content.JsonSupport;
import com.chayitong.recommendation.RecommendationModels.RecommendationItem;
import com.chayitong.recommendation.RecommendationModels.RecommendationRequest;
import com.chayitong.recommendation.RecommendationModels.RecommendationResult;
import com.chayitong.recommendation.RecommendationModels.Score;
import com.chayitong.tea.TeaEntity;
import com.chayitong.tea.TeaService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationService {

    private final SecureRandom secureRandom = new SecureRandom();
    private final QuizService quizService;
    private final TeaService teaService;
    private final RecommendationScorer scorer;
    private final RecommendationResultMapper resultMapper;
    private final RecommendationItemMapper itemMapper;
    private final JsonSupport jsonSupport;

    public RecommendationService(QuizService quizService, TeaService teaService, RecommendationScorer scorer,
            RecommendationResultMapper resultMapper, RecommendationItemMapper itemMapper, JsonSupport jsonSupport) {
        this.quizService = quizService;
        this.teaService = teaService;
        this.scorer = scorer;
        this.resultMapper = resultMapper;
        this.itemMapper = itemMapper;
        this.jsonSupport = jsonSupport;
    }

    @Transactional
    public RecommendationResult create(RecommendationRequest request) {
        if (request == null) {
            throw invalid("推荐请求不能为空");
        }
        QuizDefinitionEntity quiz = quizService.enabled(request.quizVersion());
        Map<String, String> answers = quizService.validateAnswers(quiz, request.answers());
        List<ScoredTea> scored = teaService.publishedTeas().stream()
                .map(tea -> new ScoredTea(tea, scorer.score(tea, answers, quiz.getScoringRules())))
                .sorted(Comparator.comparingInt((ScoredTea item) -> item.score().score()).reversed()
                        .thenComparing(Comparator.comparingInt((ScoredTea item) -> item.score().traitScore()).reversed())
                        .thenComparing(item -> item.tea().getSort())
                        .thenComparing(item -> item.tea().getId()))
                .toList();
        RecommendationResultEntity result = new RecommendationResultEntity();
        result.setResultId(randomResultId());
        result.setQuizDefinitionId(quiz.getId());
        result.setQuizVersion(quiz.getQuizVersion());
        result.setAnswers(jsonSupport.write(answers));
        result.setAnswersHash(answersHash(quiz.getQuizVersion(), answers));
        resultMapper.insert(result);
        int rank = 1;
        for (ScoredTea item : scored) {
            RecommendationItemEntity entity = new RecommendationItemEntity();
            entity.setResultPk(result.getId());
            entity.setTeaId(item.tea().getId());
            entity.setTeaBusinessKey(item.tea().getBusinessKey());
            entity.setRankNo(rank++);
            entity.setScore(item.score().score());
            entity.setTraitScore(item.score().traitScore());
            entity.setMatchPercent(item.score().matchPercent());
            entity.setReasons(jsonSupport.write(item.score().reasons()));
            itemMapper.insert(entity);
        }
        List<RecommendationItem> top = scored.stream().limit(3)
                .map(item -> toView(item.tea(), item.score())).toList();
        return new RecommendationResult(result.getResultId(), result.getQuizVersion(), answers,
                instant(result), top);
    }

    public RecommendationResult get(String resultId) {
        RecommendationResultEntity result = resultMapper.selectOne(new LambdaQueryWrapper<RecommendationResultEntity>()
                .eq(RecommendationResultEntity::getResultId, resultId));
        if (result == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        List<RecommendationItemEntity> items = itemMapper.selectList(new LambdaQueryWrapper<RecommendationItemEntity>()
                .eq(RecommendationItemEntity::getResultPk, result.getId())
                .le(RecommendationItemEntity::getRankNo, 3)
                .orderByAsc(RecommendationItemEntity::getRankNo));
        Map<String, TeaEntity> published = teaService.publishedByKeys(items.stream()
                .map(RecommendationItemEntity::getTeaBusinessKey).toList()).stream()
                .collect(Collectors.toMap(TeaEntity::getBusinessKey, Function.identity()));
        List<RecommendationItem> views = new ArrayList<>();
        for (RecommendationItemEntity item : items) {
            TeaEntity tea = published.get(item.getTeaBusinessKey());
            if (tea != null) {
                Score score = new Score(item.getScore(), item.getTraitScore(), item.getMatchPercent(),
                        jsonSupport.stringArray(item.getReasons()));
                views.add(toView(tea, score));
            }
        }
        return new RecommendationResult(result.getResultId(), result.getQuizVersion(), answers(result.getAnswers()),
                instant(result), views);
    }

    private RecommendationItem toView(TeaEntity tea, Score score) {
        return new RecommendationItem(teaService.toDetail(tea), score.score(), score.traitScore(),
                score.matchPercent(), score.reasons());
    }

    private Map<String, String> answers(String json) {
        Map<String, String> answers = new LinkedHashMap<>();
        var node = jsonSupport.parse(json);
        for (String key : QuizService.QUESTION_KEYS) {
            answers.put(key, node.path(key).asText());
        }
        return answers;
    }

    private String randomResultId() {
        byte[] value = new byte[16];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String answersHash(String version, Map<String, String> answers) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((version + "\n" + jsonSupport.write(answers)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Instant instant(RecommendationResultEntity result) {
        return result.getCreatedAt() == null ? null : result.getCreatedAt().toInstant(ZoneOffset.UTC);
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.PARAMETER_ERROR, message);
    }

    private record ScoredTea(TeaEntity tea, Score score) {
    }
}
