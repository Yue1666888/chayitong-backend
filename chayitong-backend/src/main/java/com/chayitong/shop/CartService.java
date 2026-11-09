package com.chayitong.shop;

import com.chayitong.auth.CurrentUser;
import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.exceptions.BusinessException;
import com.chayitong.shop.ShopDtos.CartItemResponse;
import com.chayitong.shop.ShopDtos.CartResponse;
import com.chayitong.shop.ShopRows.CartRow;
import com.chayitong.shop.ShopRows.ProductRow;
import com.chayitong.shop.ShopRows.SkuRow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

    private final JdbcTemplate jdbc;
    private final ProductService products;
    private final ShopSupport support;

    CartService(JdbcTemplate jdbc, ProductService products, ShopSupport support) {
        this.jdbc = jdbc;
        this.products = products;
        this.support = support;
    }

    public CartResponse get() {
        return view(rows(CurrentUser.require().id()));
    }

    @Transactional
    public CartResponse add(String productId, String skuId, Integer quantity) {
        long userId = CurrentUser.require().id();
        validateQuantity(quantity);
        ProductRow product = products.findProduct(productId, false, true);
        SkuRow sku = products.findSku(skuId, false);
        if (sku.productId() != product.id() || !sku.enabled()) {
            throw ShopSupport.conflict("商品规格不可售");
        }
        if (sku.availableStock() < quantity) {
            throw new BusinessException(ErrorCode.INVENTORY_INSUFFICIENT);
        }
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO cart_item(id,user_id,product_id,sku_id,quantity,checked,last_added_at)
                VALUES (?,?,?,?,?,1,?)
                ON DUPLICATE KEY UPDATE quantity=IF(cart_item.deleted=1,VALUES(quantity),cart_item.quantity+VALUES(quantity)),
                checked=1,deleted=0,last_added_at=VALUES(last_added_at)
                """, support.id(), userId, product.id(), sku.id(), quantity, now);
        CartRow row = selectedForUpdate(userId, List.of(findCartId(userId, product.id(), sku.id()))).getFirst();
        validateMergedQuantity(row.quantity(), row.sku());
        return view(rows(userId));
    }

    @Transactional
    public CartResponse patch(long id, Integer quantity, Boolean checked) {
        if (quantity == null && checked == null) {
            throw ShopSupport.badRequest("至少修改数量或勾选状态");
        }
        if (quantity != null) {
            validateQuantity(quantity);
        }
        long userId = CurrentUser.require().id();
        CartRow row = selectedForUpdate(userId, List.of(id)).getFirst();
        if (quantity != null) {
            if (!"ON_SALE".equals(row.product().status()) || !row.sku().enabled()) {
                throw ShopSupport.conflict("商品规格不可售");
            }
            validateMergedQuantity(quantity, row.sku());
        }
        jdbc.update("UPDATE cart_item SET quantity=?,checked=? WHERE id=? AND user_id=? AND deleted=0",
                quantity == null ? row.quantity() : quantity, checked == null ? row.checked() : checked, id, userId);
        return view(rows(userId));
    }

    @Transactional
    public CartResponse remove(long id) {
        long userId = CurrentUser.require().id();
        selectedForUpdate(userId, List.of(id));
        jdbc.update("UPDATE cart_item SET deleted=1 WHERE id=? AND user_id=?", id, userId);
        return view(rows(userId));
    }

    @Transactional
    public CartResponse selectAll(Boolean checked) {
        if (checked == null) {
            throw ShopSupport.badRequest("checked 不能为空");
        }
        long userId = CurrentUser.require().id();
        jdbc.update("UPDATE cart_item SET checked=? WHERE user_id=? AND deleted=0", checked, userId);
        return view(rows(userId));
    }

    @Transactional
    public CartResponse clear() {
        long userId = CurrentUser.require().id();
        jdbc.update("UPDATE cart_item SET deleted=1 WHERE user_id=? AND deleted=0", userId);
        return view(rows(userId));
    }

    List<CartRow> selectedForUpdate(long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw ShopSupport.badRequest("购物车选中项不能为空");
        }
        Set<Long> unique = new HashSet<>(ids);
        if (unique.size() != ids.size()) {
            throw ShopSupport.badRequest("购物车行不能重复");
        }
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(userId);
        args.addAll(ids);
        List<CartRow> selected = new ArrayList<>(jdbc.query("SELECT * FROM cart_item WHERE user_id=? AND deleted=0 AND id IN ("
                + placeholders + ") ORDER BY id FOR UPDATE", (rs, rowNum) -> {
            long productId = rs.getLong("product_id");
            long skuId = rs.getLong("sku_id");
            return new CartRow(rs.getLong("id"), userId, productId, skuId, rs.getInt("quantity"), rs.getBoolean("checked"),
                    productById(productId, true), skuById(skuId, true));
        }, args.toArray()));
        if (selected.size() != ids.size()) {
            throw ShopSupport.notFound();
        }
        selected.sort(java.util.Comparator.comparingLong(CartRow::id));
        return selected;
    }

    static List<Long> cartIds(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw ShopSupport.badRequest("购物车选中项不能为空");
        }
        try {
            return values.stream().map(Long::valueOf).sorted().toList();
        } catch (NumberFormatException | NullPointerException exception) {
            throw ShopSupport.badRequest("购物车行 ID 非法");
        }
    }

    private List<CartRow> rows(long userId) {
        return jdbc.query("SELECT * FROM cart_item WHERE user_id=? AND deleted=0 ORDER BY id", (rs, rowNum) -> {
            long productId = rs.getLong("product_id");
            long skuId = rs.getLong("sku_id");
            return new CartRow(rs.getLong("id"), userId, productId, skuId, rs.getInt("quantity"), rs.getBoolean("checked"),
                    productById(productId, false), skuById(skuId, false));
        }, userId);
    }

    private ProductRow productById(long id, boolean forUpdate) {
        return jdbc.query(ShopRows.PRODUCT_SELECT + " WHERE p.id=?" + (forUpdate ? " FOR UPDATE" : ""),
                ShopRows.PRODUCT, id).stream().findFirst().orElseThrow(ShopSupport::notFound);
    }

    private SkuRow skuById(long id, boolean forUpdate) {
        return jdbc.query("SELECT * FROM product_sku WHERE id=?" + (forUpdate ? " FOR UPDATE" : ""),
                ShopRows.SKU, id).stream().findFirst().orElseThrow(ShopSupport::notFound);
    }

    private CartResponse view(List<CartRow> rows) {
        int checkedCount = 0;
        int cartCount = 0;
        long total = 0;
        List<CartItemResponse> items = new ArrayList<>();
        for (CartRow row : rows) {
            String invalidReason = invalidReason(row);
            long subtotal = Math.multiplyExact(row.sku().priceFen(), row.quantity());
            if (invalidReason == null && row.checked()) {
                checkedCount += row.quantity();
                total = Math.addExact(total, subtotal);
            }
            cartCount += row.quantity();
            items.add(new CartItemResponse(Long.toString(row.id()), row.product().businessKey(), row.sku().businessKey(),
                    row.product().name(), row.sku().name(), row.product().coverUrl(), row.sku().priceFen(), row.quantity(),
                    row.checked(), subtotal, row.sku().availableStock(), invalidReason));
        }
        return new CartResponse(items, checkedCount, total, cartCount);
    }

    static String invalidReason(CartRow row) {
        if (row.product().deleted() || !"ON_SALE".equals(row.product().status())) {
            return "PRODUCT_OFF_SHELF";
        }
        if (row.sku().deleted() || !row.sku().enabled()) {
            return "SKU_DISABLED";
        }
        if (row.sku().availableStock() <= 0) {
            return "OUT_OF_STOCK";
        }
        if (row.quantity() > row.sku().availableStock()) {
            return "INSUFFICIENT_STOCK";
        }
        return null;
    }

    private static void validateQuantity(Integer quantity) {
        if (quantity == null || quantity < 1 || quantity > 99) {
            throw ShopSupport.badRequest("数量必须为 1 到 99");
        }
    }

    private static void validateMergedQuantity(int quantity, SkuRow sku) {
        if (quantity > 99) {
            throw ShopSupport.badRequest("同一规格数量不能超过 99");
        }
        if (sku.availableStock() < quantity) {
            throw new BusinessException(ErrorCode.INVENTORY_INSUFFICIENT);
        }
    }

    private long findCartId(long userId, long productId, long skuId) {
        return jdbc.queryForObject("SELECT id FROM cart_item WHERE user_id=? AND product_id=? AND sku_id=?",
                Long.class, userId, productId, skuId);
    }

}
