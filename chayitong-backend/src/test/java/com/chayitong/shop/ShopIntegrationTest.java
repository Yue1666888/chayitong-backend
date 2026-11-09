package com.chayitong.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chayitong.AbstractIntegrationTest;
import com.chayitong.auth.AuthService;
import com.chayitong.auth.AuthenticatedUser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestClient;

class ShopIntegrationTest extends AbstractIntegrationTest {

    private static final long USER_ID = 910001L;
    private static final long OTHER_USER_ID = 910002L;
    private static final long CATEGORY_ID = 910010L;
    private static final long PRODUCT_ID = 910020L;
    private static final long SKU_ID = 910030L;

    @MockitoBean
    private AuthService authenticator;

    @MockitoBean
    private com.chayitong.auth.RestClientWechatLoginClient wechatLoginClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderService orderService;

    @DynamicPropertySource
    static void demoPaymentDisabled(DynamicPropertyRegistry registry) {
        registry.add("app.shop.demo-payment-enabled", () -> "false");
    }

    @BeforeEach
    void authenticate() {
        when(authenticator.authenticate("user-token"))
                .thenReturn(new AuthenticatedUser(USER_ID, "shop_user", "USER", 1L));
        when(authenticator.authenticate("other-token"))
                .thenReturn(new AuthenticatedUser(OTHER_USER_ID, "other_shop_user", "USER", 2L));
        jdbcTemplate.update("INSERT INTO users(id,business_key,display_name,role) VALUES (?,?,?,'USER'),(?,?,?,'USER')",
                USER_ID, "shop_user", "测试用户", OTHER_USER_ID, "other_shop_user", "其他用户");
        jdbcTemplate.update("INSERT INTO product_category(id,business_key,name,slug) VALUES (?,?,?,?)",
                CATEGORY_ID, "shop_category", "测试分类", "shop-category");
    }

    @Test
    void publicProductsAndCartUseServerState() throws Exception {
        product("ON_SALE", 1000, 10);
        jdbcTemplate.update("INSERT INTO product(id,business_key,product_code,slug,category_id,name,status) "
                + "VALUES (910021,'draft_product','DRAFT-CODE','draft-code',?,'草稿商品','DRAFT')", CATEGORY_ID);

        mockMvc.perform(get("/api/products").param("categoryId", "shop_category"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value("shop_product"))
                .andExpect(jsonPath("$.data.list[0].priceFen").value(1000))
                .andExpect(jsonPath("$.data.list[0].defaultSkuId").value("shop_sku"));
        mockMvc.perform(get("/api/products/draft_product")).andExpect(status().isNotFound());

        mockMvc.perform(post("/api/cart/items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"shop_product\",\"skuId\":\"shop_sku\",\"quantity\":2}"))
                .andExpect(status().isUnauthorized());
        addCart("user-token", 2);
        MvcResult result = addCart("user-token", 1);
        JsonNode cart = data(result);
        assertThat(cart.at("/items/0/quantity").asInt()).isEqualTo(3);
        assertThat(cart.at("/checkedCount").asInt()).isEqualTo(3);
        assertThat(cart.at("/totalFen").asLong()).isEqualTo(3000);
        String cartId = cart.at("/items/0/id").asText();

        mockMvc.perform(patch("/api/cart/items/{id}", cartId).header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"checked\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkedCount").value(0))
                .andExpect(jsonPath("$.data.totalFen").value(0));
        mockMvc.perform(patch("/api/cart/items/{id}", cartId).header("Authorization", "Bearer other-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isNotFound());

        jdbcTemplate.update("UPDATE product SET status='OFF_SHELF' WHERE id=?", PRODUCT_ID);
        mockMvc.perform(get("/api/cart").header("Authorization", "Bearer user-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].invalidReason").value("PRODUCT_OFF_SHELF"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(3));
        mockMvc.perform(delete("/api/cart").header("Authorization", "Bearer user-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void orderIgnoresClientAmountsAndIsIdempotent() throws Exception {
        product("ON_SALE", 1250, 10);
        String cartId = data(addCart("user-token", 2)).at("/items/0/id").asText();
        String quoteId = quote(cartId);
        String request = orderRequest("request-1", quoteId, cartId, "自提备注", 0);

        MvcResult created = mockMvc.perform(post("/api/orders").header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payableFen").value(2500))
                .andExpect(jsonPath("$.data.productTotalFen").value(2500))
                .andExpect(jsonPath("$.data.items[0].priceFen").value(1250))
                .andExpect(jsonPath("$.data.items[0].productName").value("测试商品"))
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andReturn();
        String orderNo = data(created).get("orderNo").asText();

        MvcResult repeated = mockMvc.perform(post("/api/orders").header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andReturn();
        assertThat(data(repeated).get("orderNo").asText()).isEqualTo(orderNo);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM shop_order WHERE user_id=?", Long.class, USER_ID))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT stock_reserved FROM product_sku WHERE id=?", Long.class, SKU_ID))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT deleted FROM cart_item WHERE id=?", Integer.class, cartId))
                .isEqualTo(1);

        mockMvc.perform(post("/api/orders").header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderRequest("request-1", quoteId, cartId, "不同备注", 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

        jdbcTemplate.update("UPDATE product SET name='修改后的商品' WHERE id=?", PRODUCT_ID);
        mockMvc.perform(get("/api/orders/{orderNo}", orderNo).header("Authorization", "Bearer user-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].productName").value("测试商品"))
                .andExpect(jsonPath("$.data.items[0].priceFen").value(1250));
    }

    @Test
    void changedQuoteCannotCreateOrder() throws Exception {
        product("ON_SALE", 500, 4);
        String cartId = data(addCart("user-token", 1)).at("/items/0/id").asText();
        String quoteId = quote(cartId);
        jdbcTemplate.update("UPDATE product_sku SET price_fen=600,version=version+1 WHERE id=?", SKU_ID);

        mockMvc.perform(post("/api/orders").header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderRequest("changed-quote", quoteId, cartId, null, 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUOTE_CHANGED"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM shop_order", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT stock_reserved FROM product_sku WHERE id=?", Long.class, SKU_ID))
                .isZero();
    }

    @Test
    void expiredOrderIsClosedAndReleasesInventory() throws Exception {
        product("ON_SALE", 800, 5);
        String cartId = data(addCart("user-token", 2)).at("/items/0/id").asText();
        String quoteId = quote(cartId);
        String orderNo = data(mockMvc.perform(post("/api/orders").header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderRequest("expired-request", quoteId, cartId, null, 0)))
                .andExpect(status().isOk()).andReturn()).get("orderNo").asText();
        jdbcTemplate.update("UPDATE shop_order SET payment_expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE order_no=?", orderNo);

        assertThat(orderService.closeExpiredOrders()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT order_status FROM shop_order WHERE order_no=?", String.class, orderNo))
                .isEqualTo("CLOSED");
        assertThat(jdbcTemplate.queryForObject("SELECT stock_reserved FROM product_sku WHERE id=?", Long.class, SKU_ID))
                .isZero();
        assertThat(orderService.closeExpiredOrders()).isZero();
    }

    @Test
    void cancelReleasesOnceAndOwnershipAndDemoSwitchAreEnforced() throws Exception {
        product("ON_SALE", 800, 5);
        String cartId = data(addCart("user-token", 2)).at("/items/0/id").asText();
        String quoteId = quote(cartId);
        String orderNo = data(mockMvc.perform(post("/api/orders").header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderRequest("cancel-request", quoteId, cartId, null, 0)))
                .andExpect(status().isOk()).andReturn()).get("orderNo").asText();
        assertThat(jdbcTemplate.queryForObject("SELECT stock_reserved FROM product_sku WHERE id=?", Long.class, SKU_ID))
                .isEqualTo(2);

        mockMvc.perform(get("/api/orders/{orderNo}", orderNo).header("Authorization", "Bearer other-token"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders/{orderNo}/cancel", orderNo).header("Authorization", "Bearer other-token"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders/{orderNo}/payments", orderNo).header("Authorization", "Bearer other-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"DEMO\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders/{orderNo}/payments", orderNo).header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"DEMO\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_UNAVAILABLE"));

        mockMvc.perform(post("/api/orders/{orderNo}/cancel", orderNo).header("Authorization", "Bearer user-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        mockMvc.perform(post("/api/orders/{orderNo}/cancel", orderNo).header("Authorization", "Bearer user-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertThat(jdbcTemplate.queryForObject("SELECT stock_reserved FROM product_sku WHERE id=?", Long.class, SKU_ID))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT stock_on_hand FROM product_sku WHERE id=?", Long.class, SKU_ID))
                .isEqualTo(5);
        mockMvc.perform(post("/api/payments/wechat/notify").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_UNAVAILABLE"));
    }

    private void product(String status, long price, long stock) {
        jdbcTemplate.update("INSERT INTO product(id,business_key,product_code,slug,category_id,name,cover_url,status) "
                        + "VALUES (?,?,?,?,?,?,?,?)", PRODUCT_ID, "shop_product", "SHOP-CODE", "shop-code", CATEGORY_ID,
                "测试商品", "https://example.com/product.webp", status);
        jdbcTemplate.update("INSERT INTO product_sku(id,business_key,product_id,sku_name,sku_code,price_fen,"
                        + "original_price_fen,enabled,is_default,stock_on_hand) VALUES (?,?,?,?,?,?,?,1,1,?)",
                SKU_ID, "shop_sku", PRODUCT_ID, "100g", "SHOP-SKU", price, price + 200, stock);
    }

    private MvcResult addCart(String token, int quantity) throws Exception {
        return mockMvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"shop_product\",\"skuId\":\"shop_sku\",\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk()).andReturn();
    }

    private String quote(String cartId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/checkout/quote").header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartItemIds\":[\"" + cartId + "\"],\"pickup\":{}}"))
                .andExpect(status().isOk()).andReturn();
        return data(result).get("quoteId").asText();
    }

    private String orderRequest(String requestId, String quoteId, String cartId, String remark, long fakeAmount)
            throws Exception {
        String remarkJson = remark == null ? "null" : objectMapper.writeValueAsString(remark);
        return "{\"clientRequestId\":\"" + requestId + "\",\"quoteId\":\"" + quoteId + "\",\"cartItemIds\":[\""
                + cartId + "\"],\"pickup\":{},\"remark\":" + remarkJson + ",\"payableFen\":" + fakeAmount
                + ",\"items\":[{\"productName\":\"伪造商品\",\"priceFen\":0}]}";
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("data");
    }
}
