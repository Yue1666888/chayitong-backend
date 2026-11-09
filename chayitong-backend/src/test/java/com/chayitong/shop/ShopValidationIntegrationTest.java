package com.chayitong.shop;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chayitong.AbstractIntegrationTest;
import com.chayitong.auth.AuthService;
import com.chayitong.auth.AuthenticatedUser;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

class ShopValidationIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    private AuthService authenticator;

    @MockitoBean
    private com.chayitong.auth.RestClientWechatLoginClient wechatLoginClient;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        when(authenticator.authenticate("user-token"))
                .thenReturn(new AuthenticatedUser(950001L, "validation_user", "USER", 1L));
        when(authenticator.authenticate("admin-token"))
                .thenReturn(new AuthenticatedUser(950002L, "validation_admin", "ADMIN", 2L));
        jdbcTemplate.update("INSERT INTO users(id,business_key,display_name,role) VALUES "
                + "(950001,'validation_user','用户','USER'),(950002,'validation_admin','管理员','ADMIN')");
        jdbcTemplate.update("INSERT INTO product_category(id,business_key,name,slug) VALUES "
                + "(950010,'validation_category','校验分类','validation-category')");
    }

    @Test
    void adminCreateAndUpdateUseStrictContentBlockValidation() throws Exception {
        mockMvc.perform(post("/api/admin/products").header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productRequest(null, "[{\"type\":\"heading\",\"level\":2}]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/admin/products").header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productRequest(null,
                                "[{\"type\":\"paragraph\",\"text\":\"正文\",\"unknown\":true}]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        product();
        mockMvc.perform(put("/api/admin/products/validation_product").header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productRequest("validation_sku",
                                "[{\"type\":\"steps\",\"items\":[{\"title\":\"一步\",\"text\":\"内容\",\"unknown\":1}]}]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT detail_blocks FROM product WHERE id=950020", String.class)).isEqualTo("[]");
    }

    @Test
    void quoteDistinguishesUnavailableItemsFromInsufficientInventory() throws Exception {
        product();
        String cartId = objectMapper.readTree(mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer user-token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"validation_product\",\"skuId\":\"validation_sku\",\"quantity\":1}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray())
                .at("/data/items/0/id").asText();

        jdbcTemplate.update("UPDATE product SET status='OFF_SHELF' WHERE id=950020");
        quote(cartId, 400, "VALIDATION_ERROR");
        jdbcTemplate.update("UPDATE product SET status='ON_SALE' WHERE id=950020");
        jdbcTemplate.update("UPDATE product_sku SET enabled=0 WHERE id=950030");
        quote(cartId, 400, "VALIDATION_ERROR");
        jdbcTemplate.update("UPDATE product_sku SET enabled=1,stock_on_hand=0 WHERE id=950030");
        quote(cartId, 409, "INVENTORY_INSUFFICIENT");
    }

    private void quote(String cartId, int expectedStatus, String code) throws Exception {
        mockMvc.perform(post("/api/checkout/quote").header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartItemIds\":[\"" + cartId + "\"],\"pickup\":{}}"))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(code));
    }

    private void product() {
        jdbcTemplate.update("INSERT INTO product(id,business_key,product_code,slug,category_id,name,status) VALUES "
                + "(950020,'validation_product','VALIDATION-CODE','validation-code',950010,'校验商品','ON_SALE')");
        jdbcTemplate.update("INSERT INTO product_sku(id,business_key,product_id,sku_name,sku_code,price_fen,"
                + "original_price_fen,enabled,is_default,stock_on_hand) VALUES "
                + "(950030,'validation_sku',950020,'100g','VALIDATION-SKU',1000,1200,1,1,10)");
    }

    private String productRequest(String skuId, String blocks) {
        String id = skuId == null ? "" : "\"id\":\"" + skuId + "\",";
        return """
                {"code":"VALIDATION-CODE","categoryId":"validation_category","name":"校验商品","featured":false,
                "sort":1,"status":"ON_SALE","blocks":%s,"skus":[{%s"name":"100g","skuCode":"VALIDATION-SKU",
                "priceFen":1000,"originalPriceFen":1200,"enabled":true,"default":true,"stockOnHand":10}]}
                """.formatted(blocks, id);
    }
}
