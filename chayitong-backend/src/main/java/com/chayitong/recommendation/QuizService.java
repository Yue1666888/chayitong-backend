package com.chayitong.recommendation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.exceptions.BusinessException;
import com.chayitong.content.ContentConfigService;
import com.chayitong.content.JsonSupport;
import com.chayitong.recommendation.RecommendationModels.QuizOption;
import com.chayitong.recommendation.RecommendationModels.QuizQuestion;
import com.chayitong.recommendation.RecommendationModels.QuizResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
public class QuizService {

    public static final List<String> QUESTION_KEYS = List.of("taste", "aroma", "texture", "aftertaste",
            "strength", "time", "scene", "effort", "vessel", "temp", "season", "pairing", "explore",
            "experience", "focus");

    private final QuizDefinitionMapper quizMapper;
    private final ContentConfigService configService;
    private final JsonSupport jsonSupport;

    public QuizService(QuizDefinitionMapper quizMapper, ContentConfigService configService, JsonSupport jsonSupport) {
        this.quizMapper = quizMapper;
        this.configService = configService;
        this.jsonSupport = jsonSupport;
    }

    public QuizResponse currentQuiz() {
        QuizDefinitionEntity definition = enabled(configService.activeQuizVersion());
        return new QuizResponse(definition.getQuizVersion(), questions(definition));
    }

    public QuizDefinitionEntity enabled(String version) {
        QuizDefinitionEntity definition = quizMapper.selectOne(new LambdaQueryWrapper<QuizDefinitionEntity>()
                .eq(QuizDefinitionEntity::getQuizVersion, version)
                .eq(QuizDefinitionEntity::getStatus, "ENABLED"));
        if (definition == null) {
            throw invalid("问卷版本不存在或未启用");
        }
        return definition;
    }

    public Map<String, String> validateAnswers(QuizDefinitionEntity definition, Map<String, String> answers) {
        if (answers == null || !answers.keySet().equals(new HashSet<>(QUESTION_KEYS)) || answers.size() != QUESTION_KEYS.size()) {
            throw invalid("必须完整回答固定 15 题，且不能包含额外题目");
        }
        List<QuizQuestion> questions = questions(definition);
        Map<String, String> normalized = new LinkedHashMap<>();
        for (QuizQuestion question : questions) {
            String answer = answers.get(question.key());
            if (answer == null || question.options().stream().noneMatch(option -> option.value().equals(answer))) {
                throw invalid("题目答案不合法: " + question.key());
            }
            normalized.put(question.key(), answer);
        }
        return normalized;
    }

    private List<QuizQuestion> questions(QuizDefinitionEntity definition) {
        JsonNode nodes = jsonSupport.parse(definition.getQuestions());
        if (!nodes.isArray() || nodes.size() != 15) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "问卷定义不完整");
        }
        List<QuizQuestion> result = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (JsonNode node : nodes) {
            String key = node.path("key").asText();
            if (!node.path("title").isTextual() || !node.path("options").isArray() || keys.contains(key)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "问卷题目定义不合法");
            }
            keys.add(key);
            List<QuizOption> options = new ArrayList<>();
            Set<String> values = new HashSet<>();
            for (JsonNode option : node.path("options")) {
                String value = option.path("value").asText();
                if (!option.path("value").isTextual() || !option.path("label").isTextual() || !values.add(value)) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "问卷选项定义不合法");
                }
                options.add(new QuizOption(value, option.path("label").asText()));
            }
            result.add(new QuizQuestion(key, node.path("title").asText(), List.copyOf(options)));
        }
        if (!keys.equals(new HashSet<>(QUESTION_KEYS))) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "问卷题目集合不合法");
        }
        result.sort(java.util.Comparator.comparingInt(question -> QUESTION_KEYS.indexOf(question.key())));
        return List.copyOf(result);
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.PARAMETER_ERROR, message);
    }
}
