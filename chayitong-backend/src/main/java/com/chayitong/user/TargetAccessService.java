package com.chayitong.user;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class TargetAccessService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final com.chayitong.media.MediaService mediaService;

    public TargetAccessService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
            com.chayitong.media.MediaService mediaService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.mediaService = mediaService;
    }

    public Map<String, Object> findPublic(TargetType type, String targetId) {
        List<Map<String, Object>> rows = switch (type) {
            case TEA -> jdbcTemplate.queryForList("SELECT * FROM tea WHERE business_key = ? AND status = 'published' AND deleted = 0", targetId);
            case ARTICLE -> jdbcTemplate.queryForList("SELECT * FROM article WHERE business_key = ? AND status = 'published' AND deleted = 0", targetId);
            case PRODUCT -> jdbcTemplate.queryForList("SELECT * FROM product WHERE business_key = ? AND status = 'ON_SALE' AND deleted = 0", targetId);
            case POST -> jdbcTemplate.queryForList("SELECT p.*, u.business_key author_key, u.display_name, u.points_balance, u.avatar_media_id, "
                    + "t.business_key related_tea_key FROM post p JOIN users u ON u.id = p.author_id AND u.deleted = 0 "
                    + "LEFT JOIN tea t ON t.id = p.related_tea_id WHERE p.business_key = ? AND p.status = 'published' AND p.deleted = 0", targetId);
        };
        if (rows.isEmpty()) {
            return null;
        }
        return switch (type) {
            case TEA -> tea(rows.getFirst());
            case ARTICLE -> article(rows.getFirst());
            case PRODUCT -> product(rows.getFirst());
            case POST -> post(rows.getFirst());
        };
    }

    private Map<String, Object> tea(Map<String, Object> row) {
        Map<String, Object> result = base(row);
        result.put("slug", row.get("slug"));
        result.put("name", row.get("name"));
        result.put("aliases", strings(row.get("aliases")));
        result.put("type", row.get("category"));
        Map<String, Object> origin = new LinkedHashMap<>();
        origin.put("province", row.get("origin_province"));
        origin.put("city", row.get("origin_city"));
        origin.put("area", row.get("origin_district"));
        result.put("origin", origin);
        result.put("summary", row.get("summary"));
        result.put("cover", cover(row));
        result.put("featured", bool(row.get("featured")));
        result.put("sort", row.get("sort"));
        return result;
    }

    private Map<String, Object> article(Map<String, Object> row) {
        Map<String, Object> result = base(row);
        result.put("slug", row.get("slug"));
        result.put("title", row.get("title"));
        result.put("summary", row.get("summary"));
        result.put("category", row.get("category"));
        result.put("tags", strings(row.get("tags")));
        result.put("cover", cover(row));
        result.put("relatedTeaIds", strings(row.get("related_tea_keys")));
        result.put("readMinutes", row.get("read_minutes"));
        result.put("viewCountDisplay", row.get("view_count_display"));
        result.put("featured", bool(row.get("featured")));
        result.put("sort", row.get("sort"));
        result.put("publishedAt", instant(row.get("published_at")));
        return result;
    }

    private Map<String, Object> product(Map<String, Object> row) {
        Map<String, Object> result = base(row);
        result.put("code", row.get("product_code"));
        result.put("categoryId", firstString("SELECT business_key FROM product_category WHERE id = ?", row.get("id")));
        result.put("primaryTeaId", firstString("SELECT business_key FROM tea WHERE id = ?", row.get("primary_tea_id")));
        result.put("name", row.get("name"));
        result.put("subtitle", row.get("subtitle"));
        result.put("coverUrl", cover(row));
        result.put("galleryUrls", strings(row.get("gallery_urls")));
        result.put("tags", strings(row.get("tags")));
        result.put("relatedTeaIds", strings(row.get("related_tea_keys")));
        result.put("featured", bool(row.get("featured")));
        result.put("sort", row.get("sort"));
        result.put("status", row.get("status"));
        List<Map<String, Object>> skus = jdbcTemplate.queryForList("SELECT business_key, price_fen, original_price_fen "
                + "FROM product_sku WHERE product_id = ? AND enabled = 1 AND deleted = 0 "
                + "ORDER BY is_default DESC, id ASC LIMIT 1", row.get("id"));
        Map<String, Object> sku = skus.isEmpty() ? Map.of() : skus.getFirst();
        result.put("priceFen", sku.get("price_fen"));
        result.put("originalPriceFen", sku.get("original_price_fen"));
        result.put("defaultSkuId", sku.get("business_key"));
        return result;
    }

    private Map<String, Object> post(Map<String, Object> row) {
        Map<String, Object> result = base(row);
        Map<String, Object> author = new LinkedHashMap<>();
        author.put("id", row.get("author_key"));
        author.put("displayName", row.get("display_name"));
        author.put("avatar", mediaService.urlFor(row.get("avatar_media_id") == null ? null
                : number(row.get("avatar_media_id"))));
        author.put("level", LevelInfo.of(number(row.get("points_balance"))));
        result.put("author", author);
        result.put("content", row.get("content"));
        result.put("topic", row.get("topic"));
        result.put("tags", strings(row.get("tags")));
        List<Map<String, Object>> images = postMedia(row.get("id"), "IMAGE");
        result.put("images", images);
        List<Map<String, Object>> videos = postMedia(row.get("id"), "VIDEO");
        result.put("video", videos.isEmpty() ? null : videos.getFirst());
        String teaKey = (String) row.get("related_tea_key");
        Map<String, Object> relatedTea = new LinkedHashMap<>();
        Map<String, Object> tea = teaKey == null ? null : findPublic(TargetType.TEA, teaKey);
        relatedTea.put("available", tea != null);
        relatedTea.put("tea", tea);
        result.put("relatedTea", teaKey == null ? null : relatedTea);
        result.put("likeCount", row.get("like_count"));
        result.put("commentCount", row.get("comment_count"));
        result.put("liked", false);
        result.put("status", row.get("status"));
        result.put("publishedAt", instant(row.get("published_at")));
        result.put("createdAt", instant(row.get("created_at")));
        return result;
    }

    private List<Map<String, Object>> postMedia(Object postId, String role) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT m.media_id, m.id media_pk FROM post_media pm "
                + "JOIN media_file m ON m.id = pm.media_pk AND m.deleted = 0 AND m.status != 'DELETED' "
                + "WHERE pm.post_id = ? AND pm.media_role = ? AND pm.deleted = 0 ORDER BY pm.sort_order ASC", postId, role);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("mediaId", row.get("media_id"));
            item.put("url", mediaService.urlFor(number(row.get("media_pk"))));
            result.add(item);
        }
        return result;
    }

    private String firstString(String sql, Object id) {
        if (id == null) {
            return null;
        }
        List<String> values = jdbcTemplate.queryForList(sql, String.class, id);
        return values.isEmpty() ? null : values.getFirst();
    }

    private String cover(Map<String, Object> row) {
        Object mediaId = row.get("cover_media_id");
        String mediaUrl = mediaId == null ? null : mediaService.urlFor(number(mediaId));
        return mediaUrl == null ? (String) row.get("cover_url") : mediaUrl;
    }

    private Map<String, Object> base(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.get("business_key"));
        return result;
    }

    private List<String> strings(Object value) {
        if (value == null) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(value.toString());
            List<String> result = new ArrayList<>();
            if (node.isArray()) {
                node.forEach(item -> result.add(item.asText()));
            }
            return result;
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private boolean bool(Object value) {
        return value != null && number(value) != 0;
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    }

    private Instant instant(Object value) {
        return value instanceof Timestamp timestamp ? timestamp.toInstant() : null;
    }
}
