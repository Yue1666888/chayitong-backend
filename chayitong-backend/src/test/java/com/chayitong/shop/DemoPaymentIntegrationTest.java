package com.chayitong.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
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

class DemoPaymentIntegrationTest extends AbstractIntegrationTest {

    private static final long USER_ID = 920001L;
    private static final long ADMIN_ID = 920002L;
    private static final long SKU_ID = 920030L;

    @MockitoBean
    private AuthService authenticator;

    @MockitoBean
    private com.chayitong.auth.RestClientWechatLoginClient wechatLoginClient;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void demoPayment(DynamicPropertyRegistry registry) {
        registry.add("app.shop.demo-payment-enabled", () -> "true");
    }

    @BeforeEach
    void setUp() {
        when(authenticator.authenticate("user-token"))
                .thenReturn(new AuthenticatedUser(USER_ID, "demo_user", "USER", 1L));
        when(authenticator.authenticate("admin-token"))
                .thenReturn(new AuthenticatedUser(ADMIN_ID, "demo_admin", "ADMIN", 2L));
        jdbcTemplate.update("INSERT INTO users(id,business_key,display_name,role) VALUES (?,?,?,'USER'),(?,?,?,'ADMIN')",
                USER_ID, "demo_user", "演示用户", ADMIN_ID, "demo_admin", "管理员");
        jdbcTemplate.update("INSERT INTO product_category(id,business_key,name,slug) VALUES (920010,'demo_category','演示分类','demo-category')");
        jdbcTemplate.update("INSERT INTO product(id,business_key,product_code,slug,category_id,name,status) "
                + "VALUES (920020,'demo_product','DEMO-CODE','demo-code',920010,'演示商品','ON_SALE')");
        jdbcTemplate.update("INSERT INTO product_sku(id,business_key,product_id,sku_name,sku_code,price_fen,"
                + "original_price_fen,enabled,is_default,stock_on_hand) VALUES (?,'demo_sku',920020,'100g','DEMO-SKU',1500,1800,1,1,10)", SKU_ID);
    }

    @Test
    void demoPaymentDeductsInventoryAndAdminCompletesPickup() throws Exception {
        String cartId = data(mockMvc.perform(post("/api/cart/items").header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"demo_product\",\"skuId\":\"demo_sku\",\"quantity\":2}"))
                .andExpect(status().isOk()).andReturn()).at("/items/0/id").asText();
        String quoteId = data(mockMvc.perform(post("/api/checkout/quote").header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartItemIds\":[\"" + cartId + "\"],\"pickup\":{\"storeId\":\"store-1\"}}"))
                .andExpect(status().isOk()).andReturn()).get("quoteId").asText();
        String orderNo = data(mockMvc.perform(post("/api/orders").header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestId\":\"demo-request\",\"quoteId\":\"" + quoteId
                                + "\",\"cartItemIds\":[\"" + cartId + "\"],\"pickup\":{\"storeId\":\"store-1\"}}"))
                .andExpect(status().isOk()).andReturn()).get("orderNo").asText();

        MvcResult paid = mockMvc.perform(post("/api/orders/{orderNo}/payments", orderNo)
                        .header("Authorization", "Bearer user-token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"DEMO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentMode").value("DEMO"))
                .andExpect(jsonPath("$.data.orderStatus").value("PAID"))
                .andExpect(jsonPath("$.data.fulfillmentStatus").value("PENDING_PICKUP"))
                .andExpect(jsonPath("$.data.demoNotice").value("演示订单，不产生真实扣款"))
                .andReturn();
        String paymentNo = data(paid).get("serverPaymentNo").asText();
        assertThat(jdbcTemplate.queryForObject("SELECT stock_on_hand FROM product_sku WHERE id=?", Long.class, SKU_ID))
                .isEqualTo(8);
        assertThat(jdbcTemplate.queryForObject("SELECT stock_reserved FROM product_sku WHERE id=?", Long.class, SKU_ID))
                .isZero();

        MvcResult repeated = mockMvc.perform(post("/api/orders/{orderNo}/payments", orderNo)
                        .header("Authorization", "Bearer user-token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"DEMO\"}"))
                .andExpect(status().isOk()).andReturn();
        assertThat(data(repeated).get("serverPaymentNo").asText()).isEqualTo(paymentNo);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_transaction", Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT stock_on_hand FROM product_sku WHERE id=?", Long.class, SKU_ID))
                .isEqualTo(8);

        mockMvc.perform(post("/api/orders/{orderNo}/cancel", orderNo).header("Authorization", "Bearer user-token"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/admin/orders/{orderNo}/pickup-completion", orderNo)
                        .header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/orders/{orderNo}/pickup-completion", orderNo)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fulfillmentStatus").value("COMPLETED"));
        mockMvc.perform(post("/api/admin/orders/{orderNo}/pickup-completion", orderNo)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fulfillmentStatus").value("COMPLETED"));
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("data");
    }
}
