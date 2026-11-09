package com.chayitong.content;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chayitong.article.ArticleEntity;
import com.chayitong.article.ArticleService;
import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.exceptions.BusinessException;
import com.chayitong.content.ContentModels.AppConfig;
import com.chayitong.content.ContentModels.Home;
import com.chayitong.tea.TeaEntity;
import com.chayitong.tea.TeaService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class ContentConfigService {

    private static final Set<String> CONFIG_FIELDS = Set.of("id", "version", "revision", "seasonalTheme",
            "banners", "featuredTeaIds", "featuredArticleIds", "announcement", "features", "wikiTopics",
            "wikiHeroArticleIds", "searchHotKeywords");
    private static final Set<String> PAYLOAD_FIELDS = Set.of("seasonalTheme", "banners", "featuredTeaIds",
            "featuredArticleIds", "announcement", "features", "wikiTopics", "wikiHeroArticleIds", "searchHotKeywords");
    private static final Set<String> FORBIDDEN_FIELDS = Set.of("secret", "appsecret", "app_secret", "password",
            "privatekey", "private_key", "accesskey", "access_key", "token", "internalnote", "internalnotes",
            "internal_note", "internal_notes");

    private final AppConfigMapper configMapper;
    private final JsonSupport jsonSupport;
    private final TeaService teaService;
    private final ArticleService articleService;
    private final ContentBlockValidator blockValidator;

    public ContentConfigService(AppConfigMapper configMapper, JsonSupport jsonSupport, TeaService teaService,
            ArticleService articleService, ContentBlockValidator blockValidator) {
        this.configMapper = configMapper;
        this.jsonSupport = jsonSupport;
        this.teaService = teaService;
        this.articleService = articleService;
        this.blockValidator = blockValidator;
    }

    public AppConfig publicConfig() {
        return toResponse(requireProduction());
    }

    public Home home() {
        AppConfig config = publicConfig();
        Map<String, TeaEntity> teas = teaService.publishedByKeys(config.featuredTeaIds()).stream()
                .collect(Collectors.toMap(TeaEntity::getBusinessKey, Function.identity()));
        List<com.chayitong.tea.TeaModels.TeaSummary> featuredTeas = config.featuredTeaIds().stream()
                .map(teas::get).filter(java.util.Objects::nonNull).limit(4).map(teaService::toSummary).toList();
        Map<String, ArticleEntity> articles = articleService.publishedByKeys(config.featuredArticleIds()).stream()
                .collect(Collectors.toMap(ArticleEntity::getBusinessKey, Function.identity()));
        List<com.chayitong.article.ArticleModels.ArticleSummary> featuredArticles = config.featuredArticleIds().stream()
                .map(articles::get).filter(java.util.Objects::nonNull).map(articleService::toSummary).toList();
        return new Home(config, featuredTeas, featuredArticles);
    }

    public String activeQuizVersion() {
        String version = requireProduction().getActiveQuizVersion();
        if (version == null || version.isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "当前没有启用问卷");
        }
        return version;
    }

    @Transactional
    public AppConfig update(JsonNode request) {
        if (request == null || !request.isObject()) {
            throw invalid("配置必须为对象");
        }
        request.properties().forEach(entry -> {
            if (!CONFIG_FIELDS.contains(entry.getKey())) {
                throw invalid("配置包含未知字段: " + entry.getKey());
            }
        });
        if (!request.path("id").isTextual() || !"production".equals(request.path("id").asText())
                || !request.path("version").isInt() || request.path("version").intValue() < 1) {
            throw invalid("配置 ID 或版本不合法");
        }
        AppConfigEntity entity = requireProduction();
        if (request.has("revision") && (!request.path("revision").isIntegralNumber()
                || request.path("revision").longValue() != entity.getRevision())) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "配置版本已变化");
        }
        ObjectNode payload = jsonSupport.mapper().createObjectNode();
        request.properties().forEach(entry -> {
            if (PAYLOAD_FIELDS.contains(entry.getKey())) {
                payload.set(entry.getKey(), entry.getValue());
            }
        });
        for (String field : List.of("banners", "featuredTeaIds", "featuredArticleIds", "wikiTopics",
                "wikiHeroArticleIds", "searchHotKeywords")) {
            if (!payload.path(field).isArray()) {
                payload.putArray(field);
            }
        }
        if (!payload.has("features") || !payload.path("features").isObject()) {
            payload.putObject("features");
        }
        rejectPrivateFields(payload);
        validateReferences(payload);
        validateUrls(payload);
        entity.setConfigVersion(String.valueOf(request.path("version").intValue()));
        entity.setPayload(jsonSupport.write(payload));
        entity.setRevision(entity.getRevision() + 1);
        configMapper.updateById(entity);
        return toResponse(entity);
    }

    private AppConfig toResponse(AppConfigEntity entity) {
        JsonNode payload = jsonSupport.parse(entity.getPayload());
        return new AppConfig(entity.getConfigKey(), parseVersion(entity.getConfigVersion()),
                nullable(payload.get("seasonalTheme")), nodes(payload.path("banners")),
                strings(payload.path("featuredTeaIds")), strings(payload.path("featuredArticleIds")),
                payload.path("announcement").isTextual() ? payload.path("announcement").asText() : null,
                nullable(payload.get("features")), nodes(payload.path("wikiTopics")),
                strings(payload.path("wikiHeroArticleIds")), strings(payload.path("searchHotKeywords")));
    }

    private AppConfigEntity requireProduction() {
        AppConfigEntity entity = configMapper.selectOne(new LambdaQueryWrapper<AppConfigEntity>()
                .eq(AppConfigEntity::getConfigKey, "production"));
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公开配置不存在");
        }
        return entity;
    }

    private void validateReferences(JsonNode payload) {
        for (String teaId : strings(payload.path("featuredTeaIds"))) {
            requireTea(teaId);
        }
        List<String> articleIds = new ArrayList<>(strings(payload.path("featuredArticleIds")));
        articleIds.addAll(strings(payload.path("wikiHeroArticleIds")));
        collectArticleReferences(payload.path("wikiTopics"), articleIds);
        articleIds.stream().distinct().forEach(this::requireArticle);
    }

    private void collectArticleReferences(JsonNode node, List<String> target) {
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                if (entry.getKey().equals("articleId") && entry.getValue().isTextual()) {
                    target.add(entry.getValue().asText());
                } else if (entry.getKey().equals("articleIds") && entry.getValue().isArray()) {
                    target.addAll(strings(entry.getValue()));
                } else {
                    collectArticleReferences(entry.getValue(), target);
                }
            });
        } else if (node.isArray()) {
            node.forEach(item -> collectArticleReferences(item, target));
        }
    }

    private void validateUrls(JsonNode node) {
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                if (Set.of("src", "imageUrl", "cover").contains(entry.getKey()) && entry.getValue().isTextual()) {
                    blockValidator.validateImageUrl(entry.getValue().asText());
                } else {
                    validateUrls(entry.getValue());
                }
            });
        } else if (node.isArray()) {
            node.forEach(this::validateUrls);
        }
    }

    private void rejectPrivateFields(JsonNode node) {
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                if (FORBIDDEN_FIELDS.contains(entry.getKey().toLowerCase())) {
                    throw invalid("公开配置不能包含密钥或内部备注");
                }
                rejectPrivateFields(entry.getValue());
            });
        } else if (node.isArray()) {
            node.forEach(this::rejectPrivateFields);
        }
    }

    private void requireTea(String id) {
        try {
            teaService.require(id);
        } catch (BusinessException exception) {
            throw invalid("精选茶引用不存在");
        }
    }

    private void requireArticle(String id) {
        try {
            articleService.require(id);
        } catch (BusinessException exception) {
            throw invalid("文章引用不存在");
        }
    }

    private List<JsonNode> nodes(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<JsonNode> result = new ArrayList<>();
        node.forEach(result::add);
        return result;
    }

    private List<String> strings(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        node.forEach(item -> {
            if (!item.isTextual()) {
                throw invalid("配置 ID 数组必须只包含字符串");
            }
            result.add(item.asText());
        });
        return result;
    }

    private JsonNode nullable(JsonNode node) {
        return node == null || node.isNull() ? null : node;
    }

    private int parseVersion(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "配置版本无法读取");
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.PARAMETER_ERROR, message);
    }
}
