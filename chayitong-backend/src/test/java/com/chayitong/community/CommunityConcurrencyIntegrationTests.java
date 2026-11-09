package com.chayitong.community;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.chayitong.AbstractIntegrationTest;
import com.chayitong.auth.WechatLoginClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CommunityConcurrencyIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WechatLoginClient wechatLoginClient;

    @Test
    void concurrentDuplicateLikesOnlyIncrementOnce() throws Exception {
        long authorId = IdWorker.getId();
        long likerId = IdWorker.getId();
        long sessionId = IdWorker.getId();
        String token = UUID.randomUUID().toString();
        String postId = null;
        var executor = Executors.newFixedThreadPool(12);
        try {
            insertUser(authorId, "USER");
            insertUser(likerId, "USER");
            insertSession(sessionId, likerId, token);
            String createdPostId = responseId(mockMvc.perform(post("/api/posts")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"clientRequestId":"concurrent-like-post","content":"并发点赞","topic":"daily",
                                    "tags":[],"imageMediaIds":[],"videoMediaId":null,"relatedTeaId":null}
                                    """))
                    .andExpect(status().isOk()));
            postId = createdPostId;

            int requests = 12;
            CountDownLatch ready = new CountDownLatch(requests);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    mockMvc.perform(put("/api/posts/{id}/like", createdPostId)
                                    .header("Authorization", "Bearer " + token))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.data.liked").value(true))
                            .andExpect(jsonPath("$.data.likeCount").value(1));
                    return null;
                }));
            }
            ready.await();
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }

            long internalPostId = jdbcTemplate.queryForObject(
                    "SELECT id FROM post WHERE business_key = ?", Long.class, postId);
            Long likes = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM post_like WHERE post_id = ? AND deleted = 0", Long.class, internalPostId);
            Long likeCount = jdbcTemplate.queryForObject(
                    "SELECT like_count FROM post WHERE id = ?", Long.class, internalPostId);
            org.assertj.core.api.Assertions.assertThat(likes).isEqualTo(1);
            org.assertj.core.api.Assertions.assertThat(likeCount).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            if (postId != null) {
                jdbcTemplate.update("""
                        DELETE FROM post_like WHERE post_id = (SELECT id FROM post WHERE business_key = ?)
                        """, postId);
                jdbcTemplate.update("DELETE FROM post WHERE business_key = ?", postId);
            }
            jdbcTemplate.update("DELETE FROM auth_session WHERE id = ?", sessionId);
            jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", authorId, likerId);
        }
    }

    @Test
    void concurrentSamePostRequestReturnsTheSamePost() throws Exception {
        long authorId = IdWorker.getId();
        long sessionId = IdWorker.getId();
        String token = UUID.randomUUID().toString();
        String postId = null;
        try {
            insertUser(authorId, "USER");
            insertSession(sessionId, authorId, token);
            List<JsonNode> responses = concurrentPosts("/api/posts", token, """
                    {"clientRequestId":"same-post-request","content":"同键并发发帖","topic":"daily",
                    "tags":[],"imageMediaIds":[],"videoMediaId":null,"relatedTeaId":null}
                    """);
            postId = responses.getFirst().at("/data/id").asText();

            org.assertj.core.api.Assertions.assertThat(responses.stream()
                    .map(response -> response.at("/data/id").asText()).distinct().toList())
                    .containsExactly(postId);
            Long posts = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM post WHERE author_id = ? AND client_request_id = 'same-post-request'",
                    Long.class, authorId);
            org.assertj.core.api.Assertions.assertThat(posts).isEqualTo(1);
        } finally {
            jdbcTemplate.update("DELETE FROM post WHERE author_id = ?", authorId);
            jdbcTemplate.update("DELETE FROM auth_session WHERE id = ?", sessionId);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", authorId);
        }
    }

    @Test
    void concurrentSameCommentRequestReturnsTheSameCommentAndCount() throws Exception {
        long authorId = IdWorker.getId();
        long sessionId = IdWorker.getId();
        String token = UUID.randomUUID().toString();
        String postId = null;
        try {
            insertUser(authorId, "USER");
            insertSession(sessionId, authorId, token);
            postId = responseId(mockMvc.perform(post("/api/posts")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"clientRequestId":"comment-target-post","content":"评论目标","topic":"daily",
                                    "tags":[],"imageMediaIds":[],"videoMediaId":null,"relatedTeaId":null}
                                    """))
                    .andExpect(status().isOk()));

            List<JsonNode> responses = concurrentPosts("/api/posts/" + postId + "/comments", token, """
                    {"clientRequestId":"same-comment-request","content":"同键并发评论"}
                    """);
            String commentId = responses.getFirst().at("/data/comment/id").asText();
            org.assertj.core.api.Assertions.assertThat(responses.stream()
                    .map(response -> response.at("/data/comment/id").asText()).distinct().toList())
                    .containsExactly(commentId);
            org.assertj.core.api.Assertions.assertThat(responses)
                    .allSatisfy(response -> org.assertj.core.api.Assertions.assertThat(
                            response.at("/data/commentCount").asLong()).isEqualTo(1));

            long internalPostId = jdbcTemplate.queryForObject(
                    "SELECT id FROM post WHERE business_key = ?", Long.class, postId);
            Long comments = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM post_comment WHERE post_id = ?", Long.class, internalPostId);
            Long commentCount = jdbcTemplate.queryForObject(
                    "SELECT comment_count FROM post WHERE id = ?", Long.class, internalPostId);
            org.assertj.core.api.Assertions.assertThat(comments).isEqualTo(1);
            org.assertj.core.api.Assertions.assertThat(commentCount).isEqualTo(1);
        } finally {
            if (postId != null) {
                jdbcTemplate.update("""
                        DELETE FROM post_comment WHERE post_id = (SELECT id FROM post WHERE business_key = ?)
                        """, postId);
                jdbcTemplate.update("DELETE FROM post WHERE business_key = ?", postId);
            }
            jdbcTemplate.update("DELETE FROM auth_session WHERE id = ?", sessionId);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", authorId);
        }
    }

    private List<JsonNode> concurrentPosts(String path, String token, String body) throws Exception {
        int requests = 12;
        var executor = Executors.newFixedThreadPool(requests);
        try {
            CountDownLatch ready = new CountDownLatch(requests);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<JsonNode>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    MvcResult result = mockMvc.perform(post(path)
                                    .header("Authorization", "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.code").value("00000"))
                            .andReturn();
                    return objectMapper.readTree(result.getResponse().getContentAsByteArray());
                }));
            }
            ready.await();
            start.countDown();
            List<JsonNode> responses = new ArrayList<>();
            for (Future<JsonNode> future : futures) {
                responses.add(future.get());
            }
            return responses;
        } finally {
            executor.shutdownNow();
        }
    }

    private String responseId(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsByteArray())
                .at("/data/id").asText();
    }

    private void insertUser(long id, String role) {
        jdbcTemplate.update("""
                INSERT INTO users (id, business_key, display_name, role, points_balance, status,
                    created_at, updated_at, deleted)
                VALUES (?, ?, '并发测试用户', ?, 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """, id, "user-" + id, role);
    }

    private void insertSession(long id, long userId, String token) throws Exception {
        LocalDateTime expires = LocalDateTime.now(ZoneOffset.UTC).plusHours(1);
        jdbcTemplate.update("""
                INSERT INTO auth_session (id, user_id, access_token_hash, refresh_token_hash,
                    access_expires_at, refresh_expires_at, created_at, updated_at, deleted)
                VALUES (?, ?, ?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """, id, userId, sha256(token), sha256("refresh-" + token), expires, expires);
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
