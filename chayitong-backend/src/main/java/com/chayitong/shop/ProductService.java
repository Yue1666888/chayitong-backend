package com.chayitong.shop;

import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.api.PageResponse;
import com.chayitong.common.exceptions.BusinessException;
import com.chayitong.content.ContentBlockValidator;
import com.chayitong.shop.ShopDtos.ProductDetail;
import com.chayitong.shop.ShopDtos.ProductRequest;
import com.chayitong.shop.ShopDtos.ProductSummary;
import com.chayitong.shop.ShopDtos.SkuRequest;
import com.chayitong.shop.ShopDtos.SkuResponse;
import com.chayitong.shop.ShopRows.ProductRow;
import com.chayitong.shop.ShopRows.SkuRow;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private static final Set<String> STATUSES = Set.of("DRAFT", "ON_SALE", "OFF_SHELF");

    private final JdbcTemplate jdbc;
    private final ShopSupport support;
    private final ContentBlockValidator contentBlocks;

    ProductService(JdbcTemplate jdbc, ShopSupport support, ContentBlockValidator contentBlocks) {
        this.jdbc = jdbc;
        this.support = support;
        this.contentBlocks = contentBlocks;
    }

    public PageResponse<ProductSummary> list(
            String categoryId, String teaId, String keyword, Boolean featured, long pageNum, long pageSize) {
        validatePage(pageNum, pageSize);
        StringBuilder where = new StringBuilder(" WHERE p.deleted=0 AND p.status='ON_SALE'");
        List<Object> args = new ArrayList<>();
        if (categoryId != null && !categoryId.isBlank()) {
            where.append(" AND c.business_key=?");
            args.add(categoryId.trim());
        }
        if (teaId != null && !teaId.isBlank()) {
            where.append(" AND (t.business_key=? OR JSON_CONTAINS(p.related_tea_keys, JSON_QUOTE(?)))");
            args.add(teaId.trim());
            args.add(teaId.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + like(keyword.trim().toLowerCase()) + "%";
            where.append(" AND (LOWER(p.name) LIKE ? ESCAPE '!' OR LOWER(p.subtitle) LIKE ? ESCAPE '!'")
                    .append(" OR LOWER(p.tags) LIKE ? ESCAPE '!' OR LOWER(p.detail_blocks) LIKE ? ESCAPE '!'")
                    .append(" OR EXISTS (SELECT 1 FROM product_sku ps WHERE ps.product_id=p.id AND ps.deleted=0")
                    .append(" AND (LOWER(ps.sku_name) LIKE ? ESCAPE '!' OR LOWER(ps.sku_code) LIKE ? ESCAPE '!')))");
            for (int i = 0; i < 6; i++) {
                args.add(pattern);
            }
        }
        if (featured != null) {
            where.append(" AND p.featured=?");
            args.add(featured ? 1 : 0);
        }
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM product p JOIN product_category c ON c.id=p.category_id "
                        + "LEFT JOIN tea t ON t.id=p.primary_tea_id" + where,
                Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((pageNum - 1) * pageSize);
        List<ProductRow> products = jdbc.query(
                ShopRows.PRODUCT_SELECT + where + " ORDER BY p.sort ASC,p.id ASC LIMIT ? OFFSET ?",
                ShopRows.PRODUCT, pageArgs.toArray());
        return PageResponse.of(products.stream().map(this::summary).toList(), total == null ? 0 : total, pageNum, pageSize);
    }

    public ProductDetail publicDetail(String id) {
        ProductRow product = findProduct(id, false, true);
        return detail(product, true);
    }

    @Transactional
    public ProductDetail create(ProductRequest request) {
        validate(request);
        long productId = support.id();
        String key = support.businessKey("prd");
        try {
            jdbc.update("""
                    INSERT INTO product(id,business_key,product_code,slug,category_id,primary_tea_id,
                    related_tea_keys,name,subtitle,cover_url,gallery_urls,tags,detail_blocks,featured,sort,status)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, productId, key, request.code().trim(), slug(request.code()), categoryInternalId(request.categoryId()),
                    teaInternalId(request.primaryTeaId()), support.json(normalize(request.relatedTeaIds())),
                    request.name().trim(), ShopSupport.text(request.subtitle()), ShopSupport.text(request.coverUrl()),
                    support.json(normalize(request.galleryUrls())), support.json(normalize(request.tags())),
                    support.json(request.blocks() == null ? List.of() : request.blocks()), request.featured() ? 1 : 0,
                    request.sort(), request.status());
            for (SkuRequest sku : request.skus()) {
                insertSku(productId, sku);
            }
        } catch (DuplicateKeyException exception) {
            throw ShopSupport.conflict("商品编码或 SKU 编码已存在");
        }
        return detail(findProduct(key, false, false), false);
    }

    @Transactional
    public ProductDetail update(String id, ProductRequest request) {
        validate(request);
        ProductRow existing = findProduct(id, true, false);
        List<SkuRow> oldSkus = skus(existing.id(), false, true);
        Map<String, SkuRow> oldByKey = new HashMap<>();
        oldSkus.forEach(sku -> oldByKey.put(sku.businessKey(), sku));
        Set<String> retained = new HashSet<>();
        try {
            jdbc.update("""
                    UPDATE product SET product_code=?,slug=?,category_id=?,primary_tea_id=?,related_tea_keys=?,
                    name=?,subtitle=?,cover_url=?,gallery_urls=?,tags=?,detail_blocks=?,featured=?,sort=?,status=?
                    WHERE id=?
                    """, request.code().trim(), slug(request.code()), categoryInternalId(request.categoryId()),
                    teaInternalId(request.primaryTeaId()), support.json(normalize(request.relatedTeaIds())),
                    request.name().trim(), ShopSupport.text(request.subtitle()), ShopSupport.text(request.coverUrl()),
                    support.json(normalize(request.galleryUrls())), support.json(normalize(request.tags())),
                    support.json(request.blocks() == null ? List.of() : request.blocks()), request.featured() ? 1 : 0,
                    request.sort(), request.status(), existing.id());
            for (SkuRequest skuRequest : request.skus()) {
                if (skuRequest.id() == null || skuRequest.id().isBlank()) {
                    insertSku(existing.id(), skuRequest);
                    continue;
                }
                SkuRow old = oldByKey.get(skuRequest.id());
                if (old == null || !retained.add(old.businessKey())) {
                    throw ShopSupport.notFound();
                }
                updateSku(old, skuRequest);
            }
            for (SkuRow old : oldSkus) {
                if (!retained.contains(old.businessKey())) {
                    if (old.stockReserved() > 0) {
                        throw ShopSupport.conflict("存在预占库存的 SKU 不能删除");
                    }
                    jdbc.update("UPDATE product_sku SET deleted=1,is_default=0,version=version+1 WHERE id=?", old.id());
                }
            }
        } catch (DuplicateKeyException exception) {
            throw ShopSupport.conflict("商品编码或 SKU 编码已存在");
        }
        return detail(findProduct(id, false, false), false);
    }

    @Transactional
    public ProductDetail changeStatus(String id, String status) {
        if (!"ON_SALE".equals(status) && !"OFF_SHELF".equals(status)) {
            throw ShopSupport.badRequest("商品状态只能为 ON_SALE 或 OFF_SHELF");
        }
        ProductRow product = findProduct(id, true, false);
        if ("ON_SALE".equals(status) && skus(product.id(), true, false).isEmpty()) {
            throw ShopSupport.badRequest("上架商品至少需要一个启用规格");
        }
        jdbc.update("UPDATE product SET status=? WHERE id=?", status, product.id());
        return detail(findProduct(id, false, false), false);
    }

    ProductRow findProduct(String key, boolean forUpdate, boolean publicOnly) {
        String sql = ShopRows.PRODUCT_SELECT + " WHERE p.business_key=? AND p.deleted=0";
        List<Object> args = new ArrayList<>();
        args.add(key);
        if (publicOnly) {
            sql += " AND p.status='ON_SALE'";
        }
        if (forUpdate) {
            sql += " FOR UPDATE";
        }
        return jdbc.query(sql, ShopRows.PRODUCT, args.toArray()).stream().findFirst().orElseThrow(ShopSupport::notFound);
    }

    SkuRow findSku(String key, boolean forUpdate) {
        String sql = "SELECT * FROM product_sku WHERE business_key=? AND deleted=0" + (forUpdate ? " FOR UPDATE" : "");
        return jdbc.query(sql, ShopRows.SKU, key).stream().findFirst().orElseThrow(ShopSupport::notFound);
    }

    List<SkuRow> skus(long productId, boolean enabledOnly, boolean forUpdate) {
        String sql = "SELECT * FROM product_sku WHERE product_id=? AND deleted=0"
                + (enabledOnly ? " AND enabled=1" : "") + " ORDER BY is_default DESC,id ASC" + (forUpdate ? " FOR UPDATE" : "");
        return jdbc.query(sql, ShopRows.SKU, productId);
    }

    private ProductSummary summary(ProductRow product) {
        SkuRow defaultSku = skus(product.id(), true, false).stream().findFirst().orElse(null);
        return new ProductSummary(product.businessKey(), product.code(), product.categoryKey(), product.primaryTeaKey(),
                support.stringList(product.relatedTeaKeys()), product.name(), product.subtitle(), product.coverUrl(),
                support.stringList(product.galleryUrls()), support.stringList(product.tags()), product.featured(), product.sort(),
                product.status(), defaultSku == null ? null : defaultSku.priceFen(),
                defaultSku == null ? null : defaultSku.originalPriceFen(), defaultSku == null ? null : defaultSku.businessKey());
    }

    private ProductDetail detail(ProductRow product, boolean publicOnly) {
        List<SkuRow> skuRows = skus(product.id(), publicOnly, false);
        SkuRow defaultSku = skuRows.stream().filter(SkuRow::enabled).findFirst().orElse(null);
        List<SkuResponse> skus = skuRows.stream().map(sku -> new SkuResponse(sku.businessKey(), sku.name(), sku.code(),
                sku.priceFen(), sku.originalPriceFen(), sku.enabled(), sku.defaultSku(), sku.stockOnHand(),
                sku.stockReserved(), sku.availableStock(), sku.version())).toList();
        return new ProductDetail(product.businessKey(), product.code(), product.categoryKey(), product.primaryTeaKey(),
                support.stringList(product.relatedTeaKeys()), product.name(), product.subtitle(), product.coverUrl(),
                support.stringList(product.galleryUrls()), support.stringList(product.tags()), product.featured(), product.sort(),
                product.status(), defaultSku == null ? null : defaultSku.priceFen(),
                defaultSku == null ? null : defaultSku.originalPriceFen(), defaultSku == null ? null : defaultSku.businessKey(),
                support.nodeList(product.blocks()), skus);
    }

    private void insertSku(long productId, SkuRequest sku) {
        jdbc.update("""
                INSERT INTO product_sku(id,business_key,product_id,sku_name,sku_code,price_fen,original_price_fen,
                enabled,is_default,stock_on_hand,stock_reserved,version) VALUES (?,?,?,?,?,?,?,?,?,?,0,0)
                """, support.id(), support.businessKey("sku"), productId, sku.name().trim(), sku.skuCode().trim(),
                sku.priceFen(), sku.originalPriceFen(), sku.enabled() ? 1 : 0, sku.defaultSku() ? 1 : 0, sku.stockOnHand());
    }

    private void updateSku(SkuRow old, SkuRequest sku) {
        if (sku.stockOnHand() < old.stockReserved()) {
            throw ShopSupport.badRequest("在手库存不能小于已预占库存");
        }
        boolean changed = old.priceFen() != sku.priceFen() || old.originalPriceFen() != sku.originalPriceFen()
                || old.stockOnHand() != sku.stockOnHand() || old.enabled() != sku.enabled()
                || old.defaultSku() != sku.defaultSku();
        if (sku.version() != null && sku.version() != old.version()) {
            throw ShopSupport.conflict("SKU 版本已变化");
        }
        jdbc.update("""
                UPDATE product_sku SET sku_name=?,sku_code=?,price_fen=?,original_price_fen=?,enabled=?,is_default=?,
                stock_on_hand=?,version=? WHERE id=?
                """, sku.name().trim(), sku.skuCode().trim(), sku.priceFen(), sku.originalPriceFen(), sku.enabled() ? 1 : 0,
                sku.defaultSku() ? 1 : 0, sku.stockOnHand(), changed ? old.version() + 1 : old.version(), old.id());
    }

    private void validate(ProductRequest request) {
        if (request == null || blank(request.code()) || blank(request.categoryId()) || blank(request.name())
                || request.skus() == null || request.skus().isEmpty()) {
            throw ShopSupport.badRequest("商品编码、分类、名称和规格不能为空");
        }
        if (request.featured() == null || request.sort() == null || !STATUSES.contains(request.status())) {
            throw ShopSupport.badRequest("商品精选、排序或状态非法");
        }
        contentBlocks.validate(support.node(support.json(request.blocks() == null ? List.of() : request.blocks())), false);
        int defaults = 0;
        Set<String> codes = new HashSet<>();
        for (SkuRequest sku : request.skus()) {
            if (sku == null || blank(sku.name()) || blank(sku.skuCode()) || sku.priceFen() == null
                    || sku.originalPriceFen() == null || sku.stockOnHand() == null || sku.enabled() == null
                    || sku.defaultSku() == null || sku.priceFen() < 0 || sku.originalPriceFen() < sku.priceFen()
                    || sku.stockOnHand() < 0 || sku.stockOnHand() > Integer.MAX_VALUE || !codes.add(sku.skuCode())) {
                throw ShopSupport.badRequest("SKU 价格、库存、编码或默认标记非法");
            }
            if (sku.defaultSku()) {
                defaults++;
                if (!sku.enabled()) {
                    throw ShopSupport.badRequest("默认规格必须启用");
                }
            }
        }
        if (defaults != 1 || ("ON_SALE".equals(request.status())
                && request.skus().stream().noneMatch(sku -> sku.enabled() && sku.stockOnHand() > 0))) {
            throw ShopSupport.badRequest("商品必须有一个默认规格，上架时必须有可售库存");
        }
        validateRelatedTeas(request.relatedTeaIds());
    }

    private long categoryInternalId(String key) {
        return jdbc.query("SELECT id FROM product_category WHERE business_key=? AND deleted=0",
                (rs, rowNum) -> rs.getLong(1), key).stream().findFirst()
                .orElseThrow(() -> ShopSupport.badRequest("商品分类不存在"));
    }

    private Long teaInternalId(String key) {
        if (blank(key)) {
            return null;
        }
        return jdbc.query("SELECT id FROM tea WHERE business_key=? AND deleted=0", (rs, rowNum) -> rs.getLong(1), key)
                .stream().findFirst().orElseThrow(() -> ShopSupport.badRequest("关联茶不存在"));
    }

    private void validateRelatedTeas(List<String> keys) {
        if (keys != null) {
            keys.forEach(this::teaInternalId);
        }
    }

    private static List<String> normalize(List<String> values) {
        return values == null ? List.of() : values.stream().map(ShopSupport::text).toList();
    }

    private static String slug(String code) {
        String slug = code.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return slug.isEmpty() ? "product-" + UUIDHolder.next() : slug;
    }

    private static String like(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    static void validatePage(long pageNum, long pageSize) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "分页参数非法");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static final class UUIDHolder {
        private static String next() {
            return java.util.UUID.randomUUID().toString();
        }
    }
}
