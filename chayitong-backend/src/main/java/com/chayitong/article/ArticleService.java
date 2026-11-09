package com.chayitong.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chayitong.article.ArticleModels.ArticleDetail;
import com.chayitong.article.ArticleModels.ArticleRequest;
import com.chayitong.article.ArticleModels.ArticleSummary;
import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.api.PageResponse;
import com.chayitong.common.exceptions.BusinessException;
import com.chayitong.content.ContentBlockValidator;
import com.chayitong.content.JsonSupport;
import com.chayitong.content.PageParameters;
import com.chayitong.tea.TeaService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArticleService {

    public static final Set<String> CATEGORIES = Set.of("tea_basics", "brewing", "storage", "teaware", "tea_history", "sensory");
    private static final Set<String> ADMIN_STATUSES = Set.of("draft", "published", "offline", "withdrawn");

    private final ArticleMapper articleMapper;
    private final TeaService teaService;
    private final JsonSupport jsonSupport;
    private final ContentBlockValidator blockValidator;

    public ArticleService(ArticleMapper articleMapper, TeaService teaService, JsonSupport jsonSupport,
            ContentBlockValidator blockValidator) {
        this.articleMapper = articleMapper;
        this.teaService = teaService;
        this.jsonSupport = jsonSupport;
        this.blockValidator = blockValidator;
    }

    public PageResponse<ArticleSummary> listPublic(String category, Boolean featured, String keyword, PageParameters page) {
        validateCategory(category);
        LambdaQueryWrapper<ArticleEntity> query = baseQuery(category, featured, keyword)
                .eq(ArticleEntity::getStatus, "published");
        return page(query, page);
    }

    public PageResponse<ArticleSummary> listAdmin(String category, String status, Boolean featured,
            String keyword, PageParameters page) {
        validateCategory(category);
        if (status != null && !ADMIN_STATUSES.contains(status)) {
            throw invalid("文章状态不合法");
        }
        String storedStatus = "withdrawn".equals(status) ? "offline" : status;
        LambdaQueryWrapper<ArticleEntity> query = baseQuery(category, featured, keyword)
                .eq(storedStatus != null, ArticleEntity::getStatus, storedStatus);
        return page(query, page);
    }

    private LambdaQueryWrapper<ArticleEntity> baseQuery(String category, Boolean featured, String keyword) {
        LambdaQueryWrapper<ArticleEntity> query = new LambdaQueryWrapper<>();
        query.eq(category != null, ArticleEntity::getCategory, category)
                .eq(featured != null, ArticleEntity::getFeatured, featured)
                .and(keyword != null && !keyword.isBlank(), wrapper -> wrapper
                        .like(ArticleEntity::getTitle, keyword.trim())
                        .or().like(ArticleEntity::getSummary, keyword.trim())
                        .or().like(ArticleEntity::getTags, keyword.trim()))
                .orderByAsc(ArticleEntity::getSort, ArticleEntity::getId);
        return query;
    }

    private PageResponse<ArticleSummary> page(LambdaQueryWrapper<ArticleEntity> query, PageParameters parameters) {
        query.select(ArticleEntity::getId, ArticleEntity::getBusinessKey, ArticleEntity::getSlug,
                ArticleEntity::getTitle, ArticleEntity::getSummary, ArticleEntity::getCategory, ArticleEntity::getTags,
                ArticleEntity::getCoverUrl, ArticleEntity::getRelatedTeaKeys, ArticleEntity::getReadMinutes,
                ArticleEntity::getViewCountDisplay, ArticleEntity::getFeatured, ArticleEntity::getSort,
                ArticleEntity::getPublishedAt, ArticleEntity::getStatus, ArticleEntity::getCreatedAt,
                ArticleEntity::getUpdatedAt, ArticleEntity::getDeleted);
        Page<ArticleEntity> result = articleMapper.selectPage(new Page<>(parameters.pageNum(), parameters.pageSize()), query);
        return PageResponse.of(result.getRecords().stream().map(this::toSummary).toList(), result.getTotal(),
                parameters.pageNum(), parameters.pageSize());
    }

    public ArticleDetail getPublic(String businessKey) {
        ArticleEntity article = require(businessKey);
        if (!"published".equals(article.getStatus())) {
            throw notFound();
        }
        return toDetail(article, true);
    }

    public ArticleDetail getAdmin(String businessKey) {
        return toDetail(require(businessKey), false);
    }

    public ArticleEntity require(String businessKey) {
        ArticleEntity article = articleMapper.selectOne(new LambdaQueryWrapper<ArticleEntity>()
                .eq(ArticleEntity::getBusinessKey, businessKey));
        if (article == null) {
            throw notFound();
        }
        return article;
    }

    public List<ArticleEntity> publishedArticles() {
        return articleMapper.selectList(new LambdaQueryWrapper<ArticleEntity>()
                .eq(ArticleEntity::getStatus, "published")
                .orderByAsc(ArticleEntity::getSort, ArticleEntity::getId));
    }

    public List<ArticleEntity> publishedByKeys(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        return articleMapper.selectList(new LambdaQueryWrapper<ArticleEntity>()
                .eq(ArticleEntity::getStatus, "published")
                .in(ArticleEntity::getBusinessKey, keys));
    }

    @Transactional
    public ArticleDetail create(ArticleRequest request) {
        ArticleEntity article = new ArticleEntity();
        article.setBusinessKey(UUID.randomUUID().toString());
        article.setStatus("draft");
        apply(article, request);
        try {
            articleMapper.insert(article);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "文章 slug 已存在");
        }
        return toDetail(article, false);
    }

    @Transactional
    public ArticleDetail update(String businessKey, ArticleRequest request) {
        ArticleEntity article = require(businessKey);
        if ("published".equals(article.getStatus())) {
            throw conflict("已发布文章须先撤回再编辑");
        }
        apply(article, request);
        try {
            articleMapper.updateById(article);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "文章 slug 已存在");
        }
        return toDetail(article, false);
    }

    @Transactional
    public void delete(String businessKey) {
        ArticleEntity article = require(businessKey);
        if ("published".equals(article.getStatus())) {
            throw conflict("已发布文章须先撤回再删除");
        }
        articleMapper.deleteById(article.getId());
    }

    @Transactional
    public ArticleDetail publish(String businessKey) {
        ArticleEntity article = require(businessKey);
        if ("published".equals(article.getStatus())) {
            return toDetail(article, false);
        }
        if (!Set.of("draft", "offline").contains(article.getStatus())) {
            throw conflict("当前状态不能发布");
        }
        validateComplete(article);
        if (article.getPublishedAt() == null) {
            article.setPublishedAt(LocalDateTime.now(ZoneOffset.UTC));
        }
        article.setStatus("published");
        articleMapper.updateById(article);
        return toDetail(require(businessKey), false);
    }

    @Transactional
    public ArticleDetail withdraw(String businessKey) {
        ArticleEntity article = require(businessKey);
        if ("offline".equals(article.getStatus())) {
            return toDetail(article, false);
        }
        if (!"published".equals(article.getStatus())) {
            throw conflict("只有已发布文章可以撤回");
        }
        article.setStatus("offline");
        articleMapper.updateById(article);
        return toDetail(article, false);
    }

    public ArticleSummary toSummary(ArticleEntity article) {
        return new ArticleSummary(article.getBusinessKey(), article.getSlug(), article.getTitle(), article.getSummary(),
                article.getCategory(), jsonSupport.stringArray(article.getTags()), article.getCoverUrl(),
                jsonSupport.stringArray(article.getRelatedTeaKeys()), article.getReadMinutes(), article.getViewCountDisplay(),
                article.getFeatured(), article.getSort(), instant(article.getPublishedAt()), publicStatus(article.getStatus()),
                instant(article.getCreatedAt()), instant(article.getUpdatedAt()));
    }

    public ArticleDetail toDetail(ArticleEntity article, boolean includeRelated) {
        List<com.chayitong.tea.TeaModels.TeaSummary> related = includeRelated
                ? teaService.publishedByKeys(jsonSupport.stringArray(article.getRelatedTeaKeys())).stream()
                        .sorted(java.util.Comparator.comparing(com.chayitong.tea.TeaEntity::getSort)
                                .thenComparing(com.chayitong.tea.TeaEntity::getId))
                        .limit(3).map(teaService::toSummary).toList()
                : List.of();
        return new ArticleDetail(article.getBusinessKey(), article.getSlug(), article.getTitle(), article.getSummary(),
                article.getCategory(), jsonSupport.stringArray(article.getTags()), article.getCoverUrl(),
                jsonSupport.stringArray(article.getRelatedTeaKeys()), article.getReadMinutes(), article.getViewCountDisplay(),
                article.getFeatured(), article.getSort(), instant(article.getPublishedAt()), publicStatus(article.getStatus()),
                instant(article.getCreatedAt()), instant(article.getUpdatedAt()),
                blockValidator.validateStored(article.getContentBlocks(), jsonSupport), related);
    }

    public String searchText(ArticleEntity article) {
        return String.join(" ", article.getTitle(), article.getSummary(), article.getTags(), article.getContentBlocks(),
                article.getCategory(), categorySynonyms(article.getCategory()));
    }

    private String categorySynonyms(String category) {
        return switch (category) {
            case "tea_basics" -> "茶基础 百科";
            case "brewing" -> "冲泡";
            case "storage" -> "储存";
            case "teaware" -> "茶器";
            case "tea_history" -> "茶史 历史";
            case "sensory" -> "感官 品鉴";
            default -> "";
        };
    }

    private void apply(ArticleEntity article, ArticleRequest request) {
        if (request == null || isBlank(request.slug()) || isBlank(request.title()) || isBlank(request.summary())
                || !CATEGORIES.contains(request.category())) {
            throw invalid("文章必填字段或分类不合法");
        }
        if (request.cover() != null) {
            blockValidator.validateImageUrl(request.cover());
        }
        List<String> relatedTeaIds = request.relatedTeaIds() == null ? List.of() : request.relatedTeaIds();
        for (String teaId : relatedTeaIds) {
            try {
                teaService.require(teaId);
            } catch (BusinessException exception) {
                throw invalid("关联茶不存在: " + teaId);
            }
        }
        article.setSlug(request.slug().trim());
        article.setTitle(request.title().trim());
        article.setSummary(request.summary().trim());
        article.setCategory(request.category());
        article.setTags(jsonSupport.write(request.tags() == null ? List.of() : request.tags()));
        article.setCoverUrl(request.cover());
        article.setGalleryMediaKeys("[]");
        article.setGalleryUrls("[]");
        article.setContentBlocks(jsonSupport.write(blockValidator.validate(request.contentBlocks(), false)));
        article.setRelatedTeaKeys(jsonSupport.write(relatedTeaIds));
        article.setReadMinutes(request.readMinutes() == null ? 1 : request.readMinutes());
        article.setViewCountDisplay(request.viewCountDisplay() == null ? 0 : request.viewCountDisplay());
        article.setFeatured(Boolean.TRUE.equals(request.featured()));
        article.setSort(request.sort() == null ? 0 : request.sort());
        if (article.getReadMinutes() <= 0 || article.getViewCountDisplay() < 0) {
            throw invalid("阅读分钟数或展示阅读量不合法");
        }
    }

    private void validateComplete(ArticleEntity article) {
        if (isBlank(article.getTitle()) || isBlank(article.getSummary()) || !CATEGORIES.contains(article.getCategory())) {
            throw invalid("文章发布信息不完整");
        }
        blockValidator.validate(jsonSupport.parse(article.getContentBlocks()), true);
    }

    private String publicStatus(String status) {
        return "offline".equals(status) ? "withdrawn" : status;
    }

    private void validateCategory(String category) {
        if (category != null && !CATEGORIES.contains(category)) {
            throw invalid("文章分类不合法");
        }
    }

    private Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.PARAMETER_ERROR, message);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.STATE_CONFLICT, message);
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND);
    }
}
