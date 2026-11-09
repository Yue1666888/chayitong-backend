package com.chayitong.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chayitong.AbstractIntegrationTest;
import com.chayitong.auth.WechatLoginClient;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class UserAssetIntegrationTests extends AbstractIntegrationTest {

    @MockitoBean
    private WechatLoginClient wechatLoginClient;

    @jakarta.annotation.Resource
    private ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    private com.chayitong.auth.AuthService authService;

    @org.springframework.beans.factory.annotation.Autowired
    private UserService userService;

    @Test
    void profileIgnoresPrivilegedFieldsAndValidatesUnicodeLengths() throws Exception {
        String token = login("profile-code", "profile-openid");
        mockMvc.perform(patch("/api/me/profile").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"  茶友😀  \",\"bio\":\"  一盏清茶  \","
                                + "\"favoriteTea\":\"绿茶\",\"points\":9999,\"role\":\"ADMIN\",\"id\":\"fake\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.displayName").value("茶友😀"))
                .andExpect(jsonPath("$.data.bio").value("一盏清茶"))
                .andExpect(jsonPath("$.data.points").value(0))
                .andExpect(jsonPath("$.data.role").value("USER"));
        mockMvc.perform(patch("/api/me/profile").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"1234567890123\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(patch("/api/me/profile").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"points\":100}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dailyPointsAreAwardedOnceAndLevelsUseAllThresholds() throws Exception {
        String token = login("points-code", "points-openid");
        mockMvc.perform(post("/api/me/daily-points").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.awarded").value(true))
                .andExpect(jsonPath("$.data.points").value(5));
        mockMvc.perform(post("/api/me/daily-points").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"points\":500,\"businessDate\":\"2000-01-01\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.awarded").value(false))
                .andExpect(jsonPath("$.data.points").value(5));
        Long userId = currentInternalUserId(token);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM point_ledger WHERE user_id = ?", Long.class, userId))
                .isEqualTo(1);

        long[] thresholds = {0, 100, 250, 450, 700, 1000, 1400, 2000, 3000};
        for (int i = 0; i < thresholds.length; i++) {
            LevelInfo level = LevelInfo.of(thresholds[i]);
            assertThat(level.index()).isEqualTo(i + 1);
            assertThat(level.points()).isEqualTo(thresholds[i]);
        }
        assertThat(LevelInfo.of(125).percent()).isEqualTo(50);
        assertThat(LevelInfo.of(125).remainingPoints()).isEqualTo(125);
        assertThat(LevelInfo.of(5000).maxed()).isTrue();
        assertThat(LevelInfo.of(5000).percent()).isEqualTo(100);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentDailyPointsOnlyIncreaseBalanceOnce() throws Exception {
        when(wechatLoginClient.login("concurrent-points"))
                .thenReturn(new WechatLoginClient.WechatIdentityData("concurrent-points-openid", null));
        var tokens = authService.login("concurrent-points");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE business_key = ?", Long.class,
                tokens.user().id());
        Long sessionId = jdbcTemplate.queryForObject("SELECT id FROM auth_session WHERE user_id = ?", Long.class, userId);
        var principal = new com.chayitong.auth.AuthenticatedUser(userId, tokens.user().id(), "USER", sessionId);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(6);
        try {
            var tasks = java.util.stream.IntStream.range(0, 6)
                    .<java.util.concurrent.Callable<Boolean>>mapToObj(i -> () -> userService.claimDailyPoints(principal).awarded())
                    .toList();
            var results = executor.invokeAll(tasks);
            long awarded = 0;
            for (var result : results) {
                if (result.get()) {
                    awarded++;
                }
            }
            assertThat(awarded).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT points_balance FROM users WHERE id = ?", Long.class, userId))
                    .isEqualTo(5);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM point_ledger WHERE user_id = ?", Long.class, userId))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
            jdbcTemplate.update("DELETE FROM point_ledger WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM auth_session WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM wechat_identity WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    @Test
    void favoritesAndHistoryAreIdempotentAndUsePublicAvailability() throws Exception {
        String token = login("asset-code", "asset-openid");
        String firstTea = insertTea("published");
        String secondTea = insertTea("published");

        mockMvc.perform(put("/api/me/favorites/tea/{id}", firstTea).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.favoriteCount").value(1));
        mockMvc.perform(put("/api/me/favorites/tea/{id}", firstTea).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.favoriteCount").value(1));
        mockMvc.perform(get("/api/me/favorites").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].target.id").value(firstTea));

        mockMvc.perform(put("/api/me/history/tea/{id}", firstTea).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.available").value(true));
        mockMvc.perform(put("/api/me/history/tea/{id}", secondTea).header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/me/history/tea/{id}", firstTea).header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/me/history").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.list[0].targetId").value(firstTea));

        jdbcTemplate.update("UPDATE tea SET status = 'offline' WHERE business_key = ?", firstTea);
        mockMvc.perform(get("/api/me/favorites").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].available").value(false))
                .andExpect(jsonPath("$.data.list[0].target").doesNotExist());
        mockMvc.perform(get("/api/me/summary").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.favoriteCount").value(1))
                .andExpect(jsonPath("$.data.historyCount").value(2));

        mockMvc.perform(delete("/api/me/favorites/tea/{id}", firstTea).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(false));
        mockMvc.perform(delete("/api/me/favorites/tea/{id}", firstTea).header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/me/history/tea/{id}", secondTea).header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/me/history").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/me/history").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void assetsAreIsolatedBetweenUsers() throws Exception {
        String firstUser = login("isolation-one", "isolation-openid-one");
        String secondUser = login("isolation-two", "isolation-openid-two");
        String tea = insertTea("published");
        mockMvc.perform(put("/api/me/favorites/tea/{id}", tea).header("Authorization", bearer(firstUser)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/me/history/tea/{id}", tea).header("Authorization", bearer(firstUser)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/me/favorites").header("Authorization", bearer(secondUser)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.list").isEmpty());
        mockMvc.perform(get("/api/me/history").header("Authorization", bearer(secondUser)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(put("/api/me/favorites/tea/{id}", "missing").header("Authorization", bearer(firstUser)))
                .andExpect(status().isNotFound());
    }

    private String login(String code, String openid) throws Exception {
        when(wechatLoginClient.login(code)).thenReturn(new WechatLoginClient.WechatIdentityData(openid, null));
        String body = mockMvc.perform(post("/api/auth/wechat/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/accessToken").asText();
    }

    private String insertTea(String status) {
        long id = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);
        String key = "tea-" + id;
        jdbcTemplate.update("INSERT INTO tea (id,business_key,slug,name,category,summary,brewing,status,"
                        + "created_at,updated_at,deleted) VALUES (?,?,?,?,?,?,JSON_OBJECT(),?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)",
                id, key, key, "测试茶", "green", "测试摘要", status);
        return key;
    }

    private Long currentInternalUserId(String token) {
        return jdbcTemplate.queryForObject("SELECT user_id FROM auth_session WHERE access_token_hash = ?",
                Long.class, com.chayitong.auth.AuthService.sha256(token));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
