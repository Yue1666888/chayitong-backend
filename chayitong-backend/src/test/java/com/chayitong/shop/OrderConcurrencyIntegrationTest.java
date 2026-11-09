package com.chayitong.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.chayitong.AbstractIntegrationTest;
import com.chayitong.auth.AuthService;
import com.chayitong.auth.AuthenticatedUser;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class OrderConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    private AuthService authenticator;

    @MockitoBean
    private com.chayitong.auth.RestClientWechatLoginClient wechatLoginClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Transactional(propagation = Propagation.NEVER)
    void concurrentSameRequestReturnsOriginalOrderAndReservesOnce() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            when(authenticator.authenticate("concurrent-token"))
                    .thenReturn(new AuthenticatedUser(940001L, "concurrent_user", "USER", 1L));
            jdbcTemplate.update("INSERT INTO users(id,business_key,display_name) VALUES "
                    + "(940001,'concurrent_user','并发用户')");
            jdbcTemplate.update("INSERT INTO product_category(id,business_key,name,slug) VALUES "
                    + "(940010,'concurrent_category','并发分类','concurrent-category')");
            jdbcTemplate.update("INSERT INTO product(id,business_key,product_code,slug,category_id,name,status) VALUES "
                    + "(940020,'concurrent_product','CONCURRENT-CODE','concurrent-code',940010,'并发商品','ON_SALE')");
            jdbcTemplate.update("INSERT INTO product_sku(id,business_key,product_id,sku_name,sku_code,price_fen,"
                    + "original_price_fen,enabled,is_default,stock_on_hand) VALUES "
                    + "(940030,'concurrent_sku',940020,'100g','CONCURRENT-SKU',1000,1200,1,1,3)");
            String cartId = data(mockMvc.perform(post("/api/cart/items")
                            .header("Authorization", "Bearer concurrent-token").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productId\":\"concurrent_product\",\"skuId\":\"concurrent_sku\",\"quantity\":1}"))
                    .andReturn()).at("/items/0/id").asText();
            String quoteId = data(mockMvc.perform(post("/api/checkout/quote")
                            .header("Authorization", "Bearer concurrent-token").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"cartItemIds\":[\"" + cartId + "\"],\"pickup\":{}}"))
                    .andReturn()).get("quoteId").asText();
            String body = "{\"clientRequestId\":\"same-concurrent-request\",\"quoteId\":\"" + quoteId
                    + "\",\"cartItemIds\":[\"" + cartId + "\"],\"pickup\":{}}";

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<MvcResult> first = executor.submit(() -> createWhenReady(body, ready, start));
            Future<MvcResult> second = executor.submit(() -> createWhenReady(body, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            MvcResult firstResult = first.get(30, TimeUnit.SECONDS);
            MvcResult secondResult = second.get(30, TimeUnit.SECONDS);

            assertThat(firstResult.getResponse().getStatus()).isEqualTo(200);
            assertThat(secondResult.getResponse().getStatus()).isEqualTo(200);
            String firstOrderNo = data(firstResult).get("orderNo").asText();
            assertThat(data(secondResult).get("orderNo").asText()).isEqualTo(firstOrderNo);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM shop_order WHERE user_id=940001", Long.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT stock_reserved FROM product_sku WHERE id=940030", Long.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT stock_on_hand FROM product_sku WHERE id=940030", Long.class)).isEqualTo(3);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT deleted FROM cart_item WHERE id=?", Integer.class, cartId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            jdbcTemplate.update("DELETE FROM order_item WHERE order_id IN (SELECT id FROM shop_order WHERE user_id=940001)");
            jdbcTemplate.update("DELETE FROM payment_transaction WHERE order_id IN (SELECT id FROM shop_order WHERE user_id=940001)");
            jdbcTemplate.update("DELETE FROM shop_order WHERE user_id=940001");
            jdbcTemplate.update("DELETE FROM cart_item WHERE user_id=940001");
            jdbcTemplate.update("DELETE FROM product_sku WHERE id=940030");
            jdbcTemplate.update("DELETE FROM product WHERE id=940020");
            jdbcTemplate.update("DELETE FROM product_category WHERE id=940010");
            jdbcTemplate.update("DELETE FROM users WHERE id=940001");
        }
    }

    private MvcResult createWhenReady(String body, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await(10, TimeUnit.SECONDS);
        return mockMvc.perform(post("/api/orders").header("Authorization", "Bearer concurrent-token")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private tools.jackson.databind.JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("data");
    }
}
