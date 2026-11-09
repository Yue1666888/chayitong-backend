package com.chayitong.tea;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.api.PageResponse;
import com.chayitong.common.exceptions.BusinessException;
import com.chayitong.content.ContentBlockValidator;
import com.chayitong.content.JsonSupport;
import com.chayitong.content.PageParameters;
import com.chayitong.tea.TeaModels.Brewing;
import com.chayitong.tea.TeaModels.Origin;
import com.chayitong.tea.TeaModels.Storage;
import com.chayitong.tea.TeaModels.TeaDetail;
import com.chayitong.tea.TeaModels.TeaRequest;
import com.chayitong.tea.TeaModels.TeaSummary;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class TeaService {

    public static final Set<String> TYPES = Set.of("green", "white", "yellow", "oolong", "black", "dark");
    private static final Set<String> STATUSES = Set.of("draft", "published", "offline");

    private final TeaMapper teaMapper;
    private final JsonSupport jsonSupport;
    private final ContentBlockValidator blockValidator;
    private final JdbcTemplate jdbcTemplate;

    public TeaService(TeaMapper teaMapper, JsonSupport jsonSupport, ContentBlockValidator blockValidator,
            JdbcTemplate jdbcTemplate) {
        this.teaMapper = teaMapper;
        this.jsonSupport = jsonSupport;
        this.blockValidator = blockValidator;
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResponse<TeaSummary> listPublic(String type, String keyword, Boolean featured, PageParameters page) {
        validateType(type);
        LambdaQueryWrapper<TeaEntity> query = baseQuery(type, keyword, featured)
                .eq(TeaEntity::getStatus, "published");
        return page(query, page);
    }

    public PageResponse<TeaSummary> listAdmin(String type, String status, String keyword, PageParameters page) {
        validateType(type);
        if (status != null && !STATUSES.contains(status)) {
            throw invalid("茶状态不合法");
        }
        LambdaQueryWrapper<TeaEntity> query = baseQuery(type, keyword, null)
                .eq(status != null, TeaEntity::getStatus, status);
        return page(query, page);
    }

    private LambdaQueryWrapper<TeaEntity> baseQuery(String type, String keyword, Boolean featured) {
        LambdaQueryWrapper<TeaEntity> query = new LambdaQueryWrapper<>();
        query.eq(type != null, TeaEntity::getCategory, type)
                .eq(featured != null, TeaEntity::getFeatured, featured)
                .and(keyword != null && !keyword.isBlank(), wrapper -> wrapper
                        .like(TeaEntity::getName, keyword.trim())
                        .or().like(TeaEntity::getSummary, keyword.trim())
                        .or().like(TeaEntity::getAliases, keyword.trim()))
                .orderByAsc(TeaEntity::getSort, TeaEntity::getId);
        return query;
    }

    private PageResponse<TeaSummary> page(LambdaQueryWrapper<TeaEntity> query, PageParameters parameters) {
        Page<TeaEntity> result = teaMapper.selectPage(new Page<>(parameters.pageNum(), parameters.pageSize()), query);
        return PageResponse.of(result.getRecords().stream().map(this::toSummary).toList(), result.getTotal(),
                parameters.pageNum(), parameters.pageSize());
    }

    public TeaDetail getPublic(String businessKey) {
        TeaEntity tea = require(businessKey);
        if (!"published".equals(tea.getStatus())) {
            throw notFound();
        }
        return toDetail(tea, relatedTeas(tea));
    }

    public TeaDetail getAdmin(String businessKey) {
        return toDetail(require(businessKey), List.of());
    }

    public TeaEntity require(String businessKey) {
        TeaEntity tea = teaMapper.selectOne(new LambdaQueryWrapper<TeaEntity>()
                .eq(TeaEntity::getBusinessKey, businessKey));
        if (tea == null) {
            throw notFound();
        }
        return tea;
    }

    public List<TeaEntity> publishedTeas() {
        return teaMapper.selectList(new LambdaQueryWrapper<TeaEntity>()
                .eq(TeaEntity::getStatus, "published")
                .orderByAsc(TeaEntity::getSort, TeaEntity::getId));
    }

    public List<TeaEntity> publishedByKeys(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        return teaMapper.selectList(new LambdaQueryWrapper<TeaEntity>()
                .eq(TeaEntity::getStatus, "published")
                .in(TeaEntity::getBusinessKey, keys)
                .orderByAsc(TeaEntity::getSort, TeaEntity::getId));
    }

    @Transactional
    public TeaDetail create(TeaRequest request) {
        TeaEntity tea = new TeaEntity();
        tea.setBusinessKey(UUID.randomUUID().toString());
        apply(tea, request);
        if ("published".equals(tea.getStatus())) {
            tea.setPublishedAt(LocalDateTime.now(ZoneOffset.UTC));
        }
        try {
            teaMapper.insert(tea);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "茶 slug 已存在");
        }
        return toDetail(tea, List.of());
    }

    @Transactional
    public TeaDetail update(String businessKey, TeaRequest request) {
        TeaEntity tea = require(businessKey);
        apply(tea, request);
        if ("published".equals(tea.getStatus()) && tea.getPublishedAt() == null) {
            tea.setPublishedAt(LocalDateTime.now(ZoneOffset.UTC));
        }
        try {
            teaMapper.updateById(tea);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "茶 slug 已存在");
        }
        return toDetail(tea, List.of());
    }

    @Transactional
    public void delete(String businessKey) {
        TeaEntity tea = require(businessKey);
        teaMapper.deleteById(tea.getId());
    }

    public TeaSummary toSummary(TeaEntity tea) {
        return new TeaSummary(tea.getBusinessKey(), tea.getSlug(), tea.getName(), jsonSupport.stringArray(tea.getAliases()),
                tea.getCategory(), origin(tea), tea.getSummary(), tea.getCoverUrl(), tea.getFeatured(), tea.getSort(),
                tea.getStatus(), instant(tea.getCreatedAt()), instant(tea.getUpdatedAt()));
    }

    public TeaDetail toDetail(TeaEntity tea) {
        return toDetail(tea, List.of());
    }

    private TeaDetail toDetail(TeaEntity tea, List<TeaSummary> relatedTeas) {
        return new TeaDetail(tea.getBusinessKey(), tea.getSlug(), tea.getName(), jsonSupport.stringArray(tea.getAliases()),
                tea.getCategory(), origin(tea), tea.getSummary(), jsonSupport.stringArray(tea.getProcesses()),
                jsonSupport.stringArray(tea.getAromas()), jsonSupport.stringArray(tea.getFlavors()),
                jsonSupport.stringArray(tea.getScenes()), brewing(tea.getBrewing()),
                new Storage(tea.getStorageMethod(), tea.getAgingNote()), tea.getDrinkingNote(), tea.getCoverUrl(),
                gallery(tea), blockValidator.validateStored(tea.getContentBlocks(), jsonSupport),
                jsonSupport.stringArray(tea.getRelatedProductKeys()), tea.getFeatured(), tea.getSort(), tea.getStatus(),
                instant(tea.getCreatedAt()), instant(tea.getUpdatedAt()), relatedTeas);
    }

    public String searchText(TeaEntity tea) {
        return String.join(" ", nullToEmpty(tea.getName()), nullToEmpty(tea.getAliases()), nullToEmpty(tea.getSummary()),
                nullToEmpty(tea.getAromas()), nullToEmpty(tea.getFlavors()), nullToEmpty(tea.getProcesses()),
                nullToEmpty(tea.getScenes()), nullToEmpty(tea.getOriginProvince()), nullToEmpty(tea.getOriginCity()),
                nullToEmpty(tea.getOriginDistrict()), nullToEmpty(tea.getBrewing()), nullToEmpty(tea.getContentBlocks()),
                tea.getCategory(), typeSynonyms(tea.getCategory()));
    }

    public static String typeSynonyms(String type) {
        return switch (type) {
            case "green" -> "绿茶";
            case "white" -> "白茶";
            case "yellow" -> "黄茶";
            case "oolong" -> "青茶 乌龙茶 乌龙";
            case "black" -> "红茶";
            case "dark" -> "黑茶";
            default -> "";
        };
    }

    private List<TeaSummary> relatedTeas(TeaEntity current) {
        return publishedTeas().stream().filter(tea -> !tea.getId().equals(current.getId())).limit(3)
                .map(this::toSummary).toList();
    }

    private void apply(TeaEntity tea, TeaRequest request) {
        if (request == null) {
            throw invalid("请求体不能为空");
        }
        validate(request);
        tea.setSlug(request.slug().trim());
        tea.setName(request.name().trim());
        tea.setAliases(jsonSupport.write(nullToList(request.aliases())));
        tea.setCategory(request.type());
        tea.setOriginProvince(request.origin() == null ? null : request.origin().province());
        tea.setOriginCity(request.origin() == null ? null : request.origin().city());
        tea.setOriginDistrict(request.origin() == null ? null : request.origin().area());
        tea.setSummary(request.summary().trim());
        tea.setProcesses(jsonSupport.write(nullToList(request.craft())));
        tea.setAromas(jsonSupport.write(nullToList(request.aromas())));
        tea.setFlavors(jsonSupport.write(nullToList(request.flavors())));
        tea.setScenes(jsonSupport.write(nullToList(request.suitableScenes())));
        tea.setBrewing(jsonSupport.write(request.brew()));
        tea.setStorageMethod(request.storage() == null ? null : request.storage().method());
        tea.setAgingNote(request.storage() == null ? null : request.storage().shelfLifeHint());
        tea.setDrinkingNote(request.notice());
        tea.setCoverUrl(request.cover());
        tea.setGalleryMediaKeys("[]");
        tea.setGalleryUrls(jsonSupport.write(nullToList(request.gallery())));
        tea.setContentBlocks(jsonSupport.write(blockValidator.validate(request.contentBlocks(), false)));
        tea.setRelatedProductKeys(jsonSupport.write(nullToList(request.relatedProductIds())));
        tea.setFeatured(Boolean.TRUE.equals(request.featured()));
        tea.setSort(request.sort() == null ? 0 : request.sort());
        tea.setStatus(request.status() == null ? "draft" : request.status());
    }

    private void validate(TeaRequest request) {
        if (isBlank(request.slug()) || isBlank(request.name()) || isBlank(request.summary())
                || !TYPES.contains(request.type()) || request.status() != null && !STATUSES.contains(request.status())
                || request.brew() == null) {
            throw invalid("茶必填字段或枚举不合法");
        }
        Brewing brew = request.brew();
        positive(brew.teaGrams());
        positive(brew.waterMl());
        positive(brew.waterTempMin());
        positive(brew.waterTempMax());
        positive(brew.firstSteepSecondsMin());
        positive(brew.firstSteepSecondsMax());
        positive(brew.infusionsMin());
        positive(brew.infusionsMax());
        if (brew.waterTempMin() > brew.waterTempMax() || brew.firstSteepSecondsMin() > brew.firstSteepSecondsMax()
                || brew.infusionsMin() > brew.infusionsMax()) {
            throw invalid("冲泡范围最小值不能大于最大值");
        }
        if (request.cover() != null) {
            blockValidator.validateImageUrl(request.cover());
        }
        for (String image : nullToList(request.gallery())) {
            blockValidator.validateImageUrl(image);
        }
        List<String> productKeys = nullToList(request.relatedProductIds());
        if (!productKeys.isEmpty()) {
            String placeholders = String.join(",", productKeys.stream().map(key -> "?").toList());
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product WHERE deleted=0 AND business_key IN ("
                    + placeholders + ")", Integer.class, productKeys.toArray());
            if (count == null || count != productKeys.stream().distinct().count()) {
                throw invalid("关联商品不存在");
            }
        }
    }

    private Brewing brewing(String json) {
        try {
            JsonNode node = jsonSupport.parse(json);
            if (node.has("waterTempMin")) {
                return jsonSupport.mapper().treeToValue(node, Brewing.class);
            }
            JsonNode temperature = node.path("temperatureC");
            JsonNode firstSteep = node.path("firstSteepSeconds");
            JsonNode infusions = node.path("infusionCount");
            return new Brewing(node.path("teaGrams").asInt(), node.path("waterMl").asInt(),
                    node.path("ratio").asText(null), temperature.path("min").asInt(), temperature.path("max").asInt(),
                    node.path("vessel").isArray() ? jsonSupport.stringArray(node.path("vessel").toString()) : List.of(),
                    firstSteep.path("min").asInt(), firstSteep.path("max").asInt(),
                    node.path("laterSteepTip").asText(null), infusions.path("min").asInt(), infusions.path("max").asInt(),
                    node.path("methodTip").asText(null));
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "冲泡参数无法读取");
        }
    }

    private List<String> gallery(TeaEntity tea) {
        List<String> media = jsonSupport.stringArray(tea.getGalleryMediaKeys());
        if (!media.isEmpty()) {
            return media.stream().map(key -> "/api/media/" + key + "/content").toList();
        }
        return jsonSupport.stringArray(tea.getGalleryUrls());
    }

    private Origin origin(TeaEntity tea) {
        return new Origin(tea.getOriginProvince(), tea.getOriginCity(), tea.getOriginDistrict());
    }

    private void validateType(String type) {
        if (type != null && !TYPES.contains(type)) {
            throw invalid("茶类不合法");
        }
    }

    private void positive(Integer value) {
        if (value == null || value <= 0) {
            throw invalid("冲泡数值必须为正数");
        }
    }

    private java.time.Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static <T> List<T> nullToList(List<T> value) {
        return value == null ? List.of() : value;
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND);
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.PARAMETER_ERROR, message);
    }
}
