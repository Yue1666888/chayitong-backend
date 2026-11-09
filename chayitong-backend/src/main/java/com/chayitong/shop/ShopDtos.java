package com.chayitong.shop;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

public final class ShopDtos {

    private ShopDtos() {
    }

    public record ProductSummary(
            String id,
            String code,
            String categoryId,
            String primaryTeaId,
            List<String> relatedTeaIds,
            String name,
            String subtitle,
            String coverUrl,
            List<String> galleryUrls,
            List<String> tags,
            boolean featured,
            int sort,
            String status,
            Long priceFen,
            Long originalPriceFen,
            String defaultSkuId) {
    }

    public record SkuResponse(
            String id,
            String name,
            String skuCode,
            long priceFen,
            long originalPriceFen,
            boolean enabled,
            @JsonProperty("default") boolean defaultSku,
            long stockOnHand,
            long stockReserved,
            long availableStock,
            long version) {
    }

    public record ProductDetail(
            String id,
            String code,
            String categoryId,
            String primaryTeaId,
            List<String> relatedTeaIds,
            String name,
            String subtitle,
            String coverUrl,
            List<String> galleryUrls,
            List<String> tags,
            boolean featured,
            int sort,
            String status,
            Long priceFen,
            Long originalPriceFen,
            String defaultSkuId,
            List<JsonNode> blocks,
            List<SkuResponse> skus) {
    }

    public record SkuRequest(
            String id,
            String name,
            String skuCode,
            Long priceFen,
            Long originalPriceFen,
            Boolean enabled,
            @JsonProperty("default") Boolean defaultSku,
            Long stockOnHand,
            Long version) {
    }

    public record ProductRequest(
            String code,
            String categoryId,
            String primaryTeaId,
            List<String> relatedTeaIds,
            String name,
            String subtitle,
            String coverUrl,
            List<String> galleryUrls,
            List<String> tags,
            List<JsonNode> blocks,
            Boolean featured,
            Integer sort,
            String status,
            List<SkuRequest> skus) {
    }

    public record StatusRequest(String status) {
    }

    public record CartItemRequest(String productId, String skuId, Integer quantity) {
    }

    public record CartItemPatchRequest(Integer quantity, Boolean checked) {
    }

    public record SelectionRequest(Boolean checked) {
    }

    public record CartItemResponse(
            String id,
            String productId,
            String skuId,
            String productName,
            String skuName,
            String coverUrl,
            long priceFen,
            int quantity,
            boolean checked,
            long subtotalFen,
            long availableStock,
            String invalidReason) {
    }

    public record CartResponse(
            List<CartItemResponse> items,
            int checkedCount,
            long totalFen,
            int cartCount) {
    }

    public record Pickup(String storeId) {
    }

    public record QuoteRequest(List<String> cartItemIds, Pickup pickup) {
    }

    public record QuoteItemResponse(
            String cartItemId,
            String productId,
            String skuId,
            int quantity,
            long priceFen,
            long subtotalFen) {
    }

    public record QuoteResponse(
            String quoteId,
            Instant expiresAt,
            List<QuoteItemResponse> items,
            long productTotalFen,
            long deliveryFeeFen,
            long discountFen,
            long payableFen,
            String currency) {
    }

    public record OrderCreateRequest(
            String clientRequestId,
            String quoteId,
            List<String> cartItemIds,
            Pickup pickup,
            String remark) {
    }

    public record OrderItemResponse(
            String productId,
            String skuId,
            String skuCode,
            String productName,
            String skuName,
            String coverUrl,
            long priceFen,
            long originalPriceFen,
            int quantity,
            long subtotalFen) {
    }

    public record OrderResponse(
            String orderNo,
            String status,
            String paymentStatus,
            String fulfillmentStatus,
            String paymentMode,
            List<OrderItemResponse> items,
            long productTotalFen,
            long deliveryFeeFen,
            long discountFen,
            long payableFen,
            String currency,
            Pickup pickup,
            String remark,
            Instant paymentExpiresAt,
            Instant paidAt,
            Instant cancelledAt,
            Instant closedAt,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record PaymentRequest(String mode) {
    }

    public record PaymentResponse(
            String orderNo,
            String paymentMode,
            String channel,
            String paymentStatus,
            String orderStatus,
            String fulfillmentStatus,
            String serverPaymentNo,
            String thirdPartyTransactionNo,
            Instant paidAt,
            String demoNotice) {
    }
}
