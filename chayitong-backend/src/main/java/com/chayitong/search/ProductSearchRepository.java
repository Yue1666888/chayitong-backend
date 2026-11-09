package com.chayitong.search;

import com.chayitong.content.JsonSupport;
import com.chayitong.search.SearchModels.ProductCandidate;
import com.chayitong.search.SearchModels.ProductSummary;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProductSearchRepository {

    private final JdbcTemplate jdbcTemplate;
    private final JsonSupport jsonSupport;

    public ProductSearchRepository(JdbcTemplate jdbcTemplate, JsonSupport jsonSupport) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonSupport = jsonSupport;
    }

    public List<ProductCandidate> findOnSale() {
        List<Map<String, Object>> products = jdbcTemplate.queryForList("""
                SELECT p.id, p.business_key, p.product_code, p.related_tea_keys, p.name, p.subtitle,
                       p.cover_url, p.gallery_urls, p.tags, p.detail_blocks, p.featured, p.sort, p.status,
                       pc.business_key AS category_key, pt.business_key AS primary_tea_key
                FROM product p
                LEFT JOIN product_category pc ON pc.id = p.category_id AND pc.deleted = 0
                LEFT JOIN tea pt ON pt.id = p.primary_tea_id
                WHERE p.deleted = 0 AND p.status = 'ON_SALE'
                ORDER BY p.sort ASC, p.id ASC
                """);
        if (products.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Map<String, Object>>> skus = loadSkus(products.stream()
                .map(row -> longValue(row.get("id"))).toList());
        List<ProductCandidate> result = new ArrayList<>();
        for (Map<String, Object> row : products) {
            Long id = longValue(row.get("id"));
            List<Map<String, Object>> productSkus = skus.getOrDefault(id, List.of());
            Map<String, Object> defaultSku = productSkus.stream()
                    .filter(sku -> booleanValue(sku.get("enabled")))
                    .sorted((left, right) -> {
                        int defaultCompare = Boolean.compare(booleanValue(right.get("is_default")),
                                booleanValue(left.get("is_default")));
                        return defaultCompare != 0 ? defaultCompare
                                : Long.compare(longValue(left.get("id")), longValue(right.get("id")));
                    }).findFirst().orElse(null);
            ProductSummary summary = new ProductSummary(string(row.get("business_key")), string(row.get("product_code")),
                    string(row.get("category_key")), string(row.get("primary_tea_key")),
                    jsonSupport.stringArray(string(row.get("related_tea_keys"))), string(row.get("name")),
                    string(row.get("subtitle")), string(row.get("cover_url")),
                    jsonSupport.stringArray(string(row.get("gallery_urls"))),
                    jsonSupport.stringArray(string(row.get("tags"))), booleanValue(row.get("featured")),
                    integer(row.get("sort")), string(row.get("status")),
                    defaultSku == null ? null : longValue(defaultSku.get("price_fen")),
                    defaultSku == null ? null : longValue(defaultSku.get("original_price_fen")),
                    defaultSku == null ? null : string(defaultSku.get("business_key")));
            StringBuilder text = new StringBuilder(String.join(" ", nullToEmpty(string(row.get("name"))),
                    nullToEmpty(string(row.get("subtitle"))), nullToEmpty(string(row.get("tags"))),
                    nullToEmpty(string(row.get("detail_blocks")))));
            for (Map<String, Object> sku : productSkus) {
                text.append(' ').append(nullToEmpty(string(sku.get("sku_name"))))
                        .append(' ').append(nullToEmpty(string(sku.get("sku_code"))));
            }
            result.add(new ProductCandidate(summary, text.toString()));
        }
        return result;
    }

    private Map<Long, List<Map<String, Object>>> loadSkus(List<Long> productIds) {
        String placeholders = String.join(",", productIds.stream().map(id -> "?").toList());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, product_id, business_key, sku_name, sku_code, price_fen, original_price_fen,
                       enabled, is_default
                FROM product_sku
                WHERE deleted = 0 AND product_id IN (""" + placeholders + ") ORDER BY product_id, is_default DESC, id",
                productIds.toArray());
        Map<Long, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            result.computeIfAbsent(longValue(row.get("product_id")), key -> new ArrayList<>()).add(row);
        }
        return result;
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static Long longValue(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static Integer integer(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    private static Boolean booleanValue(Object value) {
        return value != null && (value instanceof Boolean bool ? bool : ((Number) value).intValue() != 0);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
