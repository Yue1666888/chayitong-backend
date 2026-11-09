package com.chayitong.shop;

import com.chayitong.auth.CurrentUser;
import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.api.PageResponse;
import com.chayitong.common.exceptions.BusinessException;
import com.chayitong.config.ChayitongProperties;
import com.chayitong.shop.QuoteCodec.QuoteItem;
import com.chayitong.shop.QuoteCodec.QuotePayload;
import com.chayitong.shop.ShopDtos.OrderCreateRequest;
import com.chayitong.shop.ShopDtos.OrderItemResponse;
import com.chayitong.shop.ShopDtos.OrderResponse;
import com.chayitong.shop.ShopDtos.PaymentResponse;
import com.chayitong.shop.ShopDtos.Pickup;
import com.chayitong.shop.ShopDtos.QuoteItemResponse;
import com.chayitong.shop.ShopDtos.QuoteResponse;
import com.chayitong.shop.ShopRows.CartRow;
import com.chayitong.shop.ShopRows.OrderItemRow;
import com.chayitong.shop.ShopRows.OrderRow;
import com.chayitong.shop.ShopRows.PaymentRow;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Set<String> ORDER_STATUSES = Set.of("PENDING_PAYMENT", "PAID", "CANCELLED", "CLOSED");
    private static final String DEMO_NOTICE = "演示订单，不产生真实扣款";

    private final JdbcTemplate jdbc;
    private final CartService carts;
    private final QuoteCodec quotes;
    private final ShopSupport support;
    private final ChayitongProperties properties;

    OrderService(JdbcTemplate jdbc, CartService carts, QuoteCodec quotes, ShopSupport support,
            ChayitongProperties properties) {
        this.jdbc = jdbc;
        this.carts = carts;
        this.quotes = quotes;
        this.support = support;
        this.properties = properties;
    }

    @Transactional
    public QuoteResponse quote(List<String> cartItemIds, Pickup pickup) {
        long userId = CurrentUser.require().id();
        List<Long> ids = CartService.cartIds(cartItemIds);
        Pickup normalizedPickup = normalizePickup(pickup);
        List<CartRow> rows = carts.selectedForUpdate(userId, ids);
        Instant expiresAt = Instant.now().plusSeconds(properties.shop().quoteTtlSeconds());
        List<QuoteItem> items = new ArrayList<>();
        long total = 0;
        for (CartRow row : rows) {
            String invalid = CartService.invalidReason(row);
            if (invalid != null) {
                if ("PRODUCT_OFF_SHELF".equals(invalid) || "SKU_DISABLED".equals(invalid)) {
                    throw ShopSupport.badRequest("购物车包含不可结算商品");
                }
                throw new BusinessException(ErrorCode.INVENTORY_INSUFFICIENT);
            }
            items.add(new QuoteItem(row.id(), row.productId(), row.skuId(), row.product().businessKey(),
                    row.sku().businessKey(), row.quantity(), row.sku().priceFen(), row.sku().version()));
            total = Math.addExact(total, Math.multiplyExact(row.sku().priceFen(), row.quantity()));
        }
        QuotePayload payload = new QuotePayload(userId, expiresAt.toEpochMilli(), normalizedPickup.storeId(), items);
        return new QuoteResponse(quotes.encode(payload), expiresAt, items.stream().map(item -> new QuoteItemResponse(
                Long.toString(item.cartItemId()), item.productKey(), item.skuKey(), item.quantity(), item.priceFen(),
                Math.multiplyExact(item.priceFen(), item.quantity()))).toList(), total, 0, 0, total, "CNY");
    }

    @Transactional
    public OrderResponse create(OrderCreateRequest request) {
        long userId = CurrentUser.require().id();
        if (request == null || request.clientRequestId() == null || request.clientRequestId().isBlank()
                || request.clientRequestId().length() > 64) {
            throw ShopSupport.badRequest("clientRequestId 不能为空且最多 64 字符");
        }
        jdbc.query("SELECT id FROM users WHERE id=? FOR UPDATE", (rs, rowNum) -> rs.getLong(1), userId)
                .stream().findFirst().orElseThrow(ShopSupport::notFound);
        QuotePayload payload = quotes.decode(request.quoteId());
        if (payload.userId() != userId) {
            throw ShopSupport.notFound();
        }
        Pickup pickup = normalizePickup(request.pickup());
        if (!java.util.Objects.equals(pickup.storeId(), payload.storeId())
                || !CartService.cartIds(request.cartItemIds()).equals(payload.items().stream()
                        .map(QuoteItem::cartItemId).sorted().toList())) {
            throw new BusinessException(ErrorCode.QUOTE_CHANGED);
        }
        String remark = normalizeRemark(request.remark());
        String requestHash = requestHash(payload, remark);
        OrderRow existing = findByClientRequest(userId, request.clientRequestId(), false);
        if (existing != null) {
            if (!existing.requestHash().equals(requestHash)) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            }
            return response(existing);
        }
        Instant now = Instant.now();
        if (payload.expiresAtEpochMilli() <= now.toEpochMilli()) {
            throw new BusinessException(ErrorCode.QUOTE_EXPIRED);
        }
        List<CartRow> rows = carts.selectedForUpdate(userId, CartService.cartIds(request.cartItemIds()));
        Map<Long, CartRow> rowsById = new LinkedHashMap<>();
        rows.forEach(row -> rowsById.put(row.id(), row));
        long total = 0;
        for (QuoteItem item : payload.items()) {
            CartRow row = rowsById.get(item.cartItemId());
            if (row == null || row.quantity() != item.quantity() || row.skuId() != item.skuId()
                    || row.productId() != item.productId()) {
                throw new BusinessException(ErrorCode.QUOTE_CHANGED);
            }
            if (!"ON_SALE".equals(row.product().status()) || !row.sku().enabled()
                    || row.sku().priceFen() != item.priceFen() || row.sku().version() != item.skuVersion()) {
                throw new BusinessException(ErrorCode.QUOTE_CHANGED);
            }
            total = Math.addExact(total, Math.multiplyExact(row.sku().priceFen(), row.quantity()));
        }
        long orderId = support.id();
        String orderNo = support.transactionNo("O");
        Instant paymentExpiresAt = now.plusSeconds(properties.shop().paymentTtlSeconds());
        try {
            jdbc.update("""
                    INSERT INTO shop_order(id,order_no,user_id,client_request_id,request_hash,order_status,payment_status,
                    fulfillment_status,currency,item_total_fen,delivery_fee_fen,discount_fen,payable_fen,pickup_info,remark,
                    quote_hash,quote_expires_at,payment_expires_at)
                    VALUES (?,?,?,?,?,'PENDING_PAYMENT','UNPAID','NOT_STARTED','CNY',?,0,0,?,?,?,?,?,?)
                    """, orderId, orderNo, userId, request.clientRequestId(), requestHash, total, total,
                    support.json(pickup), remark, quotes.hash(payload), Instant.ofEpochMilli(payload.expiresAtEpochMilli()),
                    paymentExpiresAt);
        } catch (DuplicateKeyException exception) {
            OrderRow concurrent = findByClientRequest(userId, request.clientRequestId(), false);
            if (concurrent != null && concurrent.requestHash().equals(requestHash)) {
                return response(concurrent);
            }
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        rows.stream().sorted(Comparator.comparingLong(CartRow::skuId)).forEach(row -> reserve(row.skuId(), row.quantity()));
        for (QuoteItem item : payload.items()) {
            CartRow row = rowsById.get(item.cartItemId());
            Map<String, Object> productSnapshot = new LinkedHashMap<>();
            productSnapshot.put("id", row.product().businessKey());
            productSnapshot.put("code", row.product().code());
            productSnapshot.put("name", row.product().name());
            productSnapshot.put("coverUrl", row.product().coverUrl());
            productSnapshot.put("tags", support.stringList(row.product().tags()));
            Map<String, Object> skuSnapshot = new LinkedHashMap<>();
            skuSnapshot.put("id", row.sku().businessKey());
            skuSnapshot.put("name", row.sku().name());
            skuSnapshot.put("skuCode", row.sku().code());
            jdbc.update("""
                    INSERT INTO order_item(id,order_id,product_id,sku_id,product_business_key,sku_code,product_name,sku_name,
                    unit_price_fen,original_price_fen,quantity,subtotal_fen,product_snapshot,sku_snapshot)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, support.id(), orderId, row.productId(), row.skuId(), row.product().businessKey(), row.sku().code(),
                    row.product().name(), row.sku().name(), row.sku().priceFen(), row.sku().originalPriceFen(), row.quantity(),
                    Math.multiplyExact(row.sku().priceFen(), row.quantity()), support.json(productSnapshot), support.json(skuSnapshot));
        }
        for (QuoteItem item : payload.items()) {
            int updated = jdbc.update("""
                    UPDATE cart_item SET deleted=IF(quantity=?,1,0),
                    quantity=IF(quantity=?,quantity,quantity-?)
                    WHERE id=? AND user_id=? AND deleted=0 AND quantity>=?
                    """, item.quantity(), item.quantity(), item.quantity(), item.cartItemId(), userId, item.quantity());
            if (updated != 1) {
                throw new BusinessException(ErrorCode.QUOTE_CHANGED);
            }
        }
        return response(findOrder(orderNo, userId, false));
    }

    public PageResponse<OrderResponse> list(String status, long pageNum, long pageSize) {
        ProductService.validatePage(pageNum, pageSize);
        if (status != null && !ORDER_STATUSES.contains(status)) {
            throw ShopSupport.badRequest("订单状态非法");
        }
        long userId = CurrentUser.require().id();
        String filter = status == null ? "" : " AND order_status=?";
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (status != null) {
            args.add(status);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM shop_order WHERE user_id=? AND deleted=0" + filter,
                Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((pageNum - 1) * pageSize);
        List<OrderRow> orders = jdbc.query("SELECT * FROM shop_order WHERE user_id=? AND deleted=0" + filter
                + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?", ShopRows.ORDER, pageArgs.toArray());
        return PageResponse.of(orders.stream().map(this::response).toList(), total == null ? 0 : total, pageNum, pageSize);
    }

    public OrderResponse get(String orderNo) {
        return response(findOrder(orderNo, CurrentUser.require().id(), false));
    }

    @Transactional
    public OrderResponse cancel(String orderNo) {
        long userId = CurrentUser.require().id();
        OrderRow order = findOrder(orderNo, userId, true);
        if ("CANCELLED".equals(order.status())) {
            return response(order);
        }
        if (!"PENDING_PAYMENT".equals(order.status())) {
            throw ShopSupport.conflict("当前订单不能取消");
        }
        releaseReserved(order.id());
        jdbc.update("""
                UPDATE shop_order SET order_status='CANCELLED',payment_status='UNPAID',cancelled_at=?,version=version+1
                WHERE id=?
                """, Instant.now(), order.id());
        return response(findOrder(orderNo, userId, false));
    }

    @Transactional
    public PaymentResponse pay(String orderNo, String mode) {
        long userId = CurrentUser.require().id();
        if (!"DEMO".equals(mode) && !"WECHAT".equals(mode)) {
            throw ShopSupport.badRequest("支付模式只能为 DEMO 或 WECHAT");
        }
        OrderRow order = findOrder(orderNo, userId, true);
        if ("PAID".equals(order.status())) {
            if (!mode.equals(order.paymentMode())) {
                throw ShopSupport.conflict("订单支付模式不可变更");
            }
            return paymentResponse(order, paidPayment(order.id()));
        }
        if (!"PENDING_PAYMENT".equals(order.status())) {
            throw ShopSupport.conflict("当前订单不能支付");
        }
        if (!order.paymentExpiresAt().isAfter(Instant.now())) {
            close(order);
            throw ShopSupport.conflict("订单已超过支付截止时间");
        }
        if (order.paymentMode() != null && !order.paymentMode().equals(mode)) {
            throw ShopSupport.conflict("订单支付模式不可变更");
        }
        if ("WECHAT".equals(mode) || !properties.shop().demoPaymentEnabled()) {
            throw new BusinessException(ErrorCode.PAYMENT_UNAVAILABLE);
        }
        Instant now = Instant.now();
        String paymentNo = support.transactionNo("P");
        jdbc.update("""
                INSERT INTO payment_transaction(id,payment_no,order_id,client_request_id,request_hash,channel,
                amount_fen,currency,status,paid_at) VALUES (?,?,?,?,?,'DEMO',?,'CNY','PAID',?)
                """, support.id(), paymentNo, order.id(), "DEMO:" + orderNo, sha256(orderNo + ":DEMO"),
                order.itemTotalFen(), now);
        items(order.id()).stream().sorted(Comparator.comparingLong(OrderItemRow::skuId)).forEach(item -> {
            int updated = jdbc.update("""
                    UPDATE product_sku SET stock_on_hand=stock_on_hand-?,stock_reserved=stock_reserved-?,version=version+1
                    WHERE id=? AND stock_on_hand>=? AND stock_reserved>=?
                    """, item.quantity(), item.quantity(), item.skuId(), item.quantity(), item.quantity());
            if (updated != 1) {
                throw new BusinessException(ErrorCode.INVENTORY_INSUFFICIENT, "支付库存扣减失败");
            }
        });
        jdbc.update("""
                UPDATE shop_order SET order_status='PAID',payment_status='PAID',fulfillment_status='PENDING_PICKUP',
                payment_mode='DEMO',paid_at=?,version=version+1 WHERE id=?
                """, now, order.id());
        OrderRow paid = findOrder(orderNo, userId, false);
        return new PaymentResponse(orderNo, "DEMO", "DEMO", "PAID", "PAID", "PENDING_PICKUP", paymentNo, null, now,
                DEMO_NOTICE);
    }

    public PaymentResponse paymentResult(String orderNo) {
        OrderRow order = findOrder(orderNo, CurrentUser.require().id(), false);
        PaymentRow payment = latestPayment(order.id());
        return new PaymentResponse(order.orderNo(), order.paymentMode(), payment == null ? null : payment.channel(),
                order.paymentStatus(), order.status(), order.fulfillmentStatus(), payment == null ? null : payment.paymentNo(),
                payment == null ? null : payment.providerTradeNo(), order.paidAt(),
                "DEMO".equals(order.paymentMode()) && "PAID".equals(order.paymentStatus()) ? DEMO_NOTICE : null);
    }

    @Transactional
    public OrderResponse completePickup(String orderNo) {
        OrderRow order = findOrder(orderNo, null, true);
        if ("COMPLETED".equals(order.fulfillmentStatus())) {
            return response(order);
        }
        if (!"PAID".equals(order.status()) || !"PAID".equals(order.paymentStatus())
                || !"PENDING_PICKUP".equals(order.fulfillmentStatus())) {
            throw ShopSupport.conflict("当前订单不能核销");
        }
        jdbc.update("UPDATE shop_order SET fulfillment_status='COMPLETED',completed_at=?,version=version+1 WHERE id=?",
                Instant.now(), order.id());
        return response(findOrder(orderNo, null, false));
    }

    @Transactional
    public int closeExpiredOrders() {
        List<OrderRow> expired = jdbc.query("""
                SELECT * FROM shop_order
                WHERE order_status='PENDING_PAYMENT' AND deleted=0 AND payment_expires_at<=?
                ORDER BY id LIMIT 100 FOR UPDATE SKIP LOCKED
                """, ShopRows.ORDER, Instant.now());
        for (OrderRow order : expired) {
            close(order);
        }
        return expired.size();
    }

    private void close(OrderRow order) {
        releaseReserved(order.id());
        jdbc.update("""
                UPDATE shop_order SET order_status='CLOSED',payment_status='UNPAID',closed_at=?,version=version+1
                WHERE id=? AND order_status='PENDING_PAYMENT'
                """, Instant.now(), order.id());
    }

    private void reserve(long skuId, int quantity) {
        int updated = jdbc.update("""
                UPDATE product_sku SET stock_reserved=stock_reserved+?,version=version+1
                WHERE id=? AND deleted=0 AND stock_on_hand-stock_reserved>=?
                """, quantity, skuId, quantity);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.INVENTORY_INSUFFICIENT);
        }
    }

    private void releaseReserved(long orderId) {
        items(orderId).stream().sorted(Comparator.comparingLong(OrderItemRow::skuId)).forEach(item -> {
            int updated = jdbc.update("""
                    UPDATE product_sku SET stock_reserved=stock_reserved-?,version=version+1 WHERE id=? AND stock_reserved>=?
                    """, item.quantity(), item.skuId(), item.quantity());
            if (updated != 1) {
                throw new BusinessException(ErrorCode.STATE_CONFLICT, "订单预占库存状态异常");
            }
        });
    }

    private OrderRow findByClientRequest(long userId, String clientRequestId, boolean forUpdate) {
        return jdbc.query("SELECT * FROM shop_order WHERE user_id=? AND client_request_id=? AND deleted=0"
                + (forUpdate ? " FOR UPDATE" : ""), ShopRows.ORDER, userId, clientRequestId).stream().findFirst().orElse(null);
    }

    private OrderRow findOrder(String orderNo, Long userId, boolean forUpdate) {
        List<Object> args = new ArrayList<>();
        args.add(orderNo);
        String sql = "SELECT * FROM shop_order WHERE order_no=? AND deleted=0";
        if (userId != null) {
            sql += " AND user_id=?";
            args.add(userId);
        }
        if (forUpdate) {
            sql += " FOR UPDATE";
        }
        return jdbc.query(sql, ShopRows.ORDER, args.toArray()).stream().findFirst().orElseThrow(ShopSupport::notFound);
    }

    private List<OrderItemRow> items(long orderId) {
        return jdbc.query("SELECT * FROM order_item WHERE order_id=? AND deleted=0 ORDER BY id", ShopRows.ORDER_ITEM, orderId);
    }

    private PaymentRow paidPayment(long orderId) {
        return jdbc.query("SELECT * FROM payment_transaction WHERE order_id=? AND status='PAID' AND deleted=0 "
                + "ORDER BY created_at DESC,id DESC LIMIT 1", ShopRows.PAYMENT, orderId).stream().findFirst()
                .orElseThrow(() -> ShopSupport.conflict("支付记录不存在"));
    }

    private PaymentRow latestPayment(long orderId) {
        return jdbc.query("SELECT * FROM payment_transaction WHERE order_id=? AND deleted=0 ORDER BY created_at DESC,id DESC LIMIT 1",
                ShopRows.PAYMENT, orderId).stream().findFirst().orElse(null);
    }

    private OrderResponse response(OrderRow order) {
        List<OrderItemResponse> items = items(order.id()).stream().map(item -> {
            String skuId = support.node(item.skuSnapshot()).path("id").asText();
            String coverUrl = support.node(item.productSnapshot()).path("coverUrl").asText(null);
            return new OrderItemResponse(item.productKey(), skuId, item.skuCode(), item.productName(), item.skuName(), coverUrl,
                    item.priceFen(), item.originalPriceFen(), item.quantity(), item.subtotalFen());
        }).toList();
        Pickup pickup = new Pickup(support.node(order.pickupInfo()).path("storeId").asText(null));
        return new OrderResponse(order.orderNo(), order.status(), order.paymentStatus(), order.fulfillmentStatus(),
                order.paymentMode(), items, order.itemTotalFen(), 0, 0, order.itemTotalFen(), "CNY", pickup, order.remark(),
                order.paymentExpiresAt(), order.paidAt(), order.cancelledAt(), order.closedAt(), order.completedAt(),
                order.createdAt(), order.updatedAt());
    }

    private PaymentResponse paymentResponse(OrderRow order, PaymentRow payment) {
        return new PaymentResponse(order.orderNo(), order.paymentMode(), payment.channel(), order.paymentStatus(), order.status(),
                order.fulfillmentStatus(), payment.paymentNo(), payment.providerTradeNo(), order.paidAt(),
                "DEMO".equals(order.paymentMode()) ? DEMO_NOTICE : null);
    }

    private static Pickup normalizePickup(Pickup pickup) {
        if (pickup == null) {
            return new Pickup(null);
        }
        String storeId = ShopSupport.text(pickup.storeId());
        if (storeId != null && storeId.isEmpty()) {
            storeId = null;
        }
        if (storeId != null && storeId.length() > 64) {
            throw ShopSupport.badRequest("自提点 ID 最多 64 字符");
        }
        return new Pickup(storeId);
    }

    private static String normalizeRemark(String remark) {
        String normalized = ShopSupport.text(remark);
        if (normalized != null && normalized.isEmpty()) {
            normalized = null;
        }
        if (normalized != null && normalized.length() > 500) {
            throw ShopSupport.badRequest("订单备注最多 500 字符");
        }
        return normalized;
    }

    private static String requestHash(QuotePayload payload, String remark) {
        StringBuilder canonical = new StringBuilder();
        payload.items().stream().sorted(Comparator.comparingLong(QuoteItem::cartItemId)).forEach(item -> {
            appendHashPart(canonical, Long.toString(item.cartItemId()));
            appendHashPart(canonical, Integer.toString(item.quantity()));
        });
        appendHashPart(canonical, payload.storeId());
        appendHashPart(canonical, remark);
        return sha256(canonical.toString());
    }

    private static void appendHashPart(StringBuilder target, String value) {
        target.append(value == null ? -1 : value.length()).append(':').append(value == null ? "" : value).append(';');
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
