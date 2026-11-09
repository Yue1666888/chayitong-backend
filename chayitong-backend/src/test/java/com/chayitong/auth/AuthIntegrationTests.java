package com.chayitong.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chayitong.AbstractIntegrationTest;
import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.exceptions.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AuthIntegrationTests extends AbstractIntegrationTest {

    @MockitoBean
    private WechatLoginClient wechatLoginClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Test
    void loginReusesIdentityAndStoresOnlyTokenHashes() throws Exception {
        when(wechatLoginClient.login("first-code"))
                .thenReturn(new WechatLoginClient.WechatIdentityData("openid-one", "union-one"));
        when(wechatLoginClient.login("second-code"))
                .thenReturn(new WechatLoginClient.WechatIdentityData("openid-one", "union-one"));

        JsonNode first = login("first-code");
        JsonNode second = login("second-code");

        assertThat(first.at("/data/user/id").asText()).isEqualTo(second.at("/data/user/id").asText());
        assertThat(first.toString()).doesNotContain("openid-one", "union-one");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE business_key = ?",
                Long.class, first.at("/data/user/id").asText());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM wechat_identity WHERE user_id = ?",
                Long.class, userId)).isEqualTo(1);
        String storedAccessHash = jdbcTemplate.queryForObject("SELECT access_token_hash FROM auth_session WHERE user_id = ? ORDER BY created_at DESC LIMIT 1",
                String.class, userId);
        assertThat(storedAccessHash).hasSize(64).isNotEqualTo(second.at("/data/accessToken").asText());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_session WHERE access_token_hash = ? OR refresh_token_hash = ?",
                Long.class, second.at("/data/accessToken").asText(), second.at("/data/refreshToken").asText())).isZero();
    }

    @Test
    void refreshRotatesBothTokensAndLogoutRevokesSession() throws Exception {
        when(wechatLoginClient.login("rotate-code"))
                .thenReturn(new WechatLoginClient.WechatIdentityData("openid-rotate", null));
        JsonNode login = login("rotate-code").at("/data");
        String oldAccess = login.at("/accessToken").asText();
        String oldRefresh = login.at("/refreshToken").asText();

        JsonNode refreshed = objectMapper.readTree(mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("00000"))
                .andReturn().getResponse().getContentAsString()).at("/data");
        String newAccess = refreshed.at("/accessToken").asText();
        String newRefresh = refreshed.at("/refreshToken").asText();
        assertThat(newAccess).isNotEqualTo(oldAccess);
        assertThat(newRefresh).isNotEqualTo(oldRefresh);

        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + oldAccess))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("00000"));
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidWechatCodeAndExpiredAccessTokenAreUnauthorized() throws Exception {
        when(wechatLoginClient.login("bad-code")).thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));
        mockMvc.perform(post("/api/auth/wechat/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"bad-code\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        when(wechatLoginClient.login("expire-code"))
                .thenReturn(new WechatLoginClient.WechatIdentityData("openid-expire", null));
        String access = login("expire-code").at("/data/accessToken").asText();
        jdbcTemplate.update("UPDATE auth_session SET access_expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND "
                + "WHERE access_token_hash = ?", AuthService.sha256(access));
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blankRefreshTokenIsAlwaysUnauthorized() throws Exception {
        for (String body : new String[]{"{\"refreshToken\":\"\"}", "{\"refreshToken\":\"   \"}",
                "{\"refreshToken\":null}", "{}"}) {
            mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentFirstLoginReusesIdentityAndRollsBackExtraUsers() throws Exception {
        when(wechatLoginClient.login("concurrent-first-code"))
                .thenReturn(new WechatLoginClient.WechatIdentityData("concurrent-first-openid", null));
        var originalUserIds = new java.util.HashSet<Long>(jdbcTemplate.queryForList("SELECT id FROM users", Long.class));
        var executor = java.util.concurrent.Executors.newFixedThreadPool(6);
        try {
            var tasks = java.util.stream.IntStream.range(0, 6)
                    .<java.util.concurrent.Callable<TokenResponse>>mapToObj(i -> () -> authService.login("concurrent-first-code"))
                    .toList();
            var responses = executor.invokeAll(tasks);
            var businessIds = new java.util.HashSet<String>();
            for (var response : responses) {
                businessIds.add(response.get().user().id());
            }
            assertThat(businessIds).hasSize(1);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM wechat_identity WHERE openid = ?",
                    Long.class, "concurrent-first-openid")).isEqualTo(1);
            Long userId = jdbcTemplate.queryForObject("SELECT user_id FROM wechat_identity WHERE openid = ?",
                    Long.class, "concurrent-first-openid");
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_session WHERE user_id = ?",
                    Long.class, userId)).isEqualTo(6);
            var newUserIds = new java.util.HashSet<Long>(jdbcTemplate.queryForList("SELECT id FROM users", Long.class));
            newUserIds.removeAll(originalUserIds);
            assertThat(newUserIds).containsExactly(userId);
        } finally {
            executor.shutdownNow();
            var newUserIds = new java.util.HashSet<Long>(jdbcTemplate.queryForList("SELECT id FROM users", Long.class));
            newUserIds.removeAll(originalUserIds);
            for (Long userId : newUserIds) {
                jdbcTemplate.update("DELETE FROM auth_session WHERE user_id = ?", userId);
                jdbcTemplate.update("DELETE FROM wechat_identity WHERE user_id = ?", userId);
                jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
            }
        }
    }

    private JsonNode login(String code) throws Exception {
        String body = mockMvc.perform(post("/api/auth/wechat/login")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }
}
