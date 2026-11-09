package com.chayitong.shop;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.RowMapper;

final class ShopRows {

    private ShopRows() {
    }

    record ProductRow(
            long id,
            String businessKey,
            String code,
            String categoryKey,
            String primaryTeaKey,
            String relatedTeaKeys,
            String name,
            String subtitle,
            String coverUrl,
            String galleryUrls,
            String tags,
            String blocks,
            boolean featured,
            int sort,
            String status,
            boolean deleted) {
    }

    record SkuRow(
            long id,
            long productId,
            String businessKey,
            String name,
            String code,
            long priceFen,
            long originalPriceFen,
            boolean enabled,
            boolean defaultSku,
            long stockOnHand,
            long stockReserved,
            long version,
            boolean deleted) {
        long availableStock() {
            return stockOnHand - stockReserved;
        }
    }

    record CartRow(
            long id,
            long userId,
            long productId,
            long skuId,
            int quantity,
            boolean checked,
            ProductRow product,
            SkuRow sku) {
    }

    record OrderRow(
            long id,
            String orderNo,
            long userId,
            String clientRequestId,
            String requestHash,
            String status,
            String paymentStatus,
            String fulfillmentStatus,
            String paymentMode,
            long itemTotalFen,
            String pickupInfo,
            String remark,
            Instant paymentExpiresAt,
            Instant paidAt,
            Instant cancelledAt,
            Instant closedAt,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt) {
    }

    record OrderItemRow(
            long id,
            long orderId,
            long productId,
            long skuId,
            String productKey,
            String skuCode,
            String productName,
            String skuName,
            long priceFen,
            long originalPriceFen,
            int quantity,
            long subtotalFen,
            String productSnapshot,
            String skuSnapshot) {
    }

    record PaymentRow(
            String paymentNo,
            String channel,
            String status,
            String providerTradeNo,
            Instant paidAt) {
    }

    static final RowMapper<ProductRow> PRODUCT = (rs, rowNum) -> new ProductRow(
            rs.getLong("id"),
            rs.getString("business_key"),
            rs.getString("product_code"),
            rs.getString("category_key"),
            rs.getString("primary_tea_key"),
            rs.getString("related_tea_keys"),
            rs.getString("name"),
            rs.getString("subtitle"),
            rs.getString("cover_url"),
            rs.getString("gallery_urls"),
            rs.getString("tags"),
            rs.getString("detail_blocks"),
            rs.getBoolean("featured"),
            rs.getInt("sort"),
            rs.getString("status"),
            rs.getBoolean("deleted"));

    static final RowMapper<SkuRow> SKU = (rs, rowNum) -> new SkuRow(
            rs.getLong("id"),
            rs.getLong("product_id"),
            rs.getString("business_key"),
            rs.getString("sku_name"),
            rs.getString("sku_code"),
            rs.getLong("price_fen"),
            rs.getLong("original_price_fen"),
            rs.getBoolean("enabled"),
            rs.getBoolean("is_default"),
            rs.getLong("stock_on_hand"),
            rs.getLong("stock_reserved"),
            rs.getLong("version"),
            rs.getBoolean("deleted"));

    static final RowMapper<OrderRow> ORDER = (rs, rowNum) -> new OrderRow(
            rs.getLong("id"),
            rs.getString("order_no"),
            rs.getLong("user_id"),
            rs.getString("client_request_id"),
            rs.getString("request_hash"),
            rs.getString("order_status"),
            rs.getString("payment_status"),
            rs.getString("fulfillment_status"),
            rs.getString("payment_mode"),
            rs.getLong("item_total_fen"),
            rs.getString("pickup_info"),
            rs.getString("remark"),
            instant(rs, "payment_expires_at"),
            instant(rs, "paid_at"),
            instant(rs, "cancelled_at"),
            instant(rs, "closed_at"),
            instant(rs, "completed_at"),
            instant(rs, "created_at"),
            instant(rs, "updated_at"));

    static final RowMapper<OrderItemRow> ORDER_ITEM = (rs, rowNum) -> new OrderItemRow(
            rs.getLong("id"),
            rs.getLong("order_id"),
            rs.getLong("product_id"),
            rs.getLong("sku_id"),
            rs.getString("product_business_key"),
            rs.getString("sku_code"),
            rs.getString("product_name"),
            rs.getString("sku_name"),
            rs.getLong("unit_price_fen"),
            rs.getLong("original_price_fen"),
            rs.getInt("quantity"),
            rs.getLong("subtotal_fen"),
            rs.getString("product_snapshot"),
            rs.getString("sku_snapshot"));

    static final RowMapper<PaymentRow> PAYMENT = (rs, rowNum) -> new PaymentRow(
            rs.getString("payment_no"),
            rs.getString("channel"),
            rs.getString("status"),
            rs.getString("provider_trade_no"),
            instant(rs, "paid_at"));

    static final String PRODUCT_SELECT = """
            SELECT p.*, c.business_key AS category_key, t.business_key AS primary_tea_key
            FROM product p
            JOIN product_category c ON c.id = p.category_id
            LEFT JOIN tea t ON t.id = p.primary_tea_id
            """;

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
