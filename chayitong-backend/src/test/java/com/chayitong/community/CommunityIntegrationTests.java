package com.chayitong.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chayitong.AbstractIntegrationTest;
import com.chayitong.auth.WechatLoginClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class CommunityIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WechatLoginClient wechatLoginClient;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM post_comment");
        jdbcTemplate.update("DELETE FROM post_like");
        jdbcTemplate.update("DELETE FROM post_media");
        jdbcTemplate.update("DELETE FROM post");
        insertUser(1001L, "USER", 125);
        insertUser(1002L, "USER", 0);
        insertUser(1003L, "ADMIN", 0);
        insertSession(9001L, 1001L, "token-1001");
        insertSession(9002L, 1002L, "token-1002");
        insertSession(9003L, 1003L, "token-1003");
        jdbcTemplate.update("""
                INSERT INTO tea (id, business_key, slug, name, aliases, category, summary, brewing,
                    gallery_media_keys, content_blocks, related_product_keys, featured, sort, status,
                    published_at, created_at, updated_at, deleted)
                VALUES (7001, 'tea-7001', 'tea-7001', '测试茶', JSON_ARRAY(), 'green', '测试茶摘要',
                    JSON_OBJECT(), JSON_ARRAY(), JSON_ARRAY(), JSON_ARRAY(), 0, 0, 'published', UTC_TIMESTAMP(6),
                    UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """);
    }

    @Test
    void createsPureTextPostAndEnforcesValidationAndRequestFingerprint() throws Exception {
        String content = "茶".repeat(300);
        JsonNode created = responseJson(createPost(1001L, "post-request-1", content, "tasting", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("published"))
                .andExpect(jsonPath("$.data.author.id").value("user-1001"))
                .andExpect(jsonPath("$.data.author.level.index").value(2))
                .andExpect(jsonPath("$.data.content").value(content))
                .andExpect(jsonPath("$.data.likeCount").value(0))
                .andExpect(jsonPath("$.data.commentCount").value(0)));
        String postId = created.at("/data/id").asText();

        createPost(1001L, "post-request-1", content, "tasting", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(postId));
        createPost(1001L, "post-request-1", "不同正文", "tasting", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post WHERE author_id = 1001 AND client_request_id = 'post-request-1'",
                Long.class)).isEqualTo(1);

        createPost(1001L, "blank", "  ", "tasting", null).andExpect(status().isBadRequest());
        createPost(1001L, "too-long", "茶".repeat(301), "tasting", null)
                .andExpect(status().isBadRequest());
        createPost(1001L, "bad-topic", "正文", "all", null).andExpect(status().isBadRequest());
        createPost(1001L, "bad-tea", "正文", "tasting", "missing-tea")
                .andExpect(status().isBadRequest());

        ObjectNode unknown = postBody("unknown", "正文", "tasting");
        unknown.put("status", "hidden");
        mockMvc.perform(post("/api/posts").with(token(1001L)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(unknown)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bindsNineImagesOrOneVideoAndRejectsInvalidMedia() throws Exception {
        for (int i = 0; i < 9; i++) {
            insertMedia(8000L + i, "media-" + i, 1001L, "IMAGE", "UPLOADED");
        }
        ObjectNode request = postBody("nine-images", "九图动态", "daily");
        var images = request.putArray("imageMediaIds");
        for (int i = 0; i < 9; i++) {
            images.add("media-" + i);
        }
        JsonNode created = responseJson(performPost(request, 1001L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images.length()").value(9))
                .andExpect(jsonPath("$.data.images[0].mediaId").value("media-0"))
                .andExpect(jsonPath("$.data.images[8].mediaId").value("media-8")));
        String postId = created.at("/data/id").asText();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM media_file WHERE bound_type = 'post' AND bound_id = ? AND status = 'BOUND'
                """, Long.class, postId)).isEqualTo(9);

        ObjectNode tenImages = postBody("ten-images", "十图", "daily");
        var ten = tenImages.putArray("imageMediaIds");
        for (int i = 0; i < 10; i++) {
            ten.add("media-" + i);
        }
        performPost(tenImages, 1001L).andExpect(status().isBadRequest());

        insertMedia(8100L, "video-1", 1001L, "VIDEO", "UPLOADED");
        ObjectNode mixed = postBody("mixed", "图视混合", "daily");
        mixed.putArray("imageMediaIds").add("media-0");
        mixed.put("videoMediaId", "video-1");
        performPost(mixed, 1001L).andExpect(status().isBadRequest());

        insertMedia(8101L, "other-image", 1002L, "IMAGE", "UPLOADED");
        ObjectNode other = postBody("other-media", "他人媒体", "daily");
        other.putArray("imageMediaIds").add("other-image");
        performPost(other, 1001L).andExpect(status().isForbidden());

        ObjectNode reused = postBody("reused", "已绑定媒体", "daily");
        reused.putArray("imageMediaIds").add("media-0");
        performPost(reused, 1001L).andExpect(status().isBadRequest());

        ObjectNode video = postBody("one-video", "视频动态", "daily");
        video.put("videoMediaId", "video-1");
        performPost(video, 1001L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.video.mediaId").value("video-1"))
                .andExpect(jsonPath("$.data.images.length()").value(0));
    }

    @Test
    void filtersAndStablyOrdersPublicAndOwnPosts() throws Exception {
        String first = responseJson(createPost(1001L, "order-1", "第一条", "tasting", "tea-7001")
                .andExpect(status().isOk())).at("/data/id").asText();
        String second = responseJson(createPost(1001L, "order-2", "第二条", "daily", null)
                .andExpect(status().isOk())).at("/data/id").asText();
        createPost(1002L, "order-3", "其他作者", "tasting", null).andExpect(status().isOk());
        jdbcTemplate.update("UPDATE post SET published_at = '2026-01-01 00:00:00' WHERE business_key IN (?, ?)",
                first, second);

        mockMvc.perform(get("/api/posts").param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3));
        mockMvc.perform(get("/api/posts").param("topic", "tasting").param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.list.length()").value(1));
        mockMvc.perform(get("/api/posts").param("topic", "invalid")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/posts").param("pageSize", "101")).andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/me/posts").with(token(1001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.list[0].id").value(second))
                .andExpect(jsonPath("$.data.list[1].id").value(first));
        mockMvc.perform(get("/api/posts/{id}", first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.relatedTea.available").value(true))
                .andExpect(jsonPath("$.data.relatedTea.tea.id").value("tea-7001"));

        jdbcTemplate.update("UPDATE tea SET status = 'offline' WHERE id = 7001");
        mockMvc.perform(get("/api/posts/{id}", first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.relatedTea.available").value(false))
                .andExpect(jsonPath("$.data.relatedTea.tea").doesNotExist());
    }

    @Test
    void supportsAdminHideRestoreWithoutUsingAuthorDelete() throws Exception {
        String postId = responseJson(createPost(1001L, "visibility", "待隐藏", "daily", null)
                .andExpect(status().isOk())).at("/data/id").asText();

        ObjectNode hidden = objectMapper.createObjectNode().put("status", "hidden");
        mockMvc.perform(put("/api/admin/posts/{id}/visibility", postId).with(token(1001L))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(hidden)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/posts/{id}/visibility", postId).with(token(1003L))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(hidden)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("hidden"));
        mockMvc.perform(get("/api/posts/{id}", postId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/posts/{id}/comments", postId)).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/posts/{id}/like", postId).with(token(1002L)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/me/posts").with(token(1001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].status").value("hidden"));

        ObjectNode published = objectMapper.createObjectNode().put("status", "published");
        mockMvc.perform(put("/api/admin/posts/{id}/visibility", postId).with(token(1003L))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(published)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/posts/{id}", postId)).andExpect(status().isOk());

        mockMvc.perform(delete("/api/posts/{id}", postId).with(token(1001L))).andExpect(status().isOk());
        mockMvc.perform(put("/api/admin/posts/{id}/visibility", postId).with(token(1003L))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(published)))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyAuthorDeletesPostAndRepeatedDeleteIsIdempotent() throws Exception {
        String postId = responseJson(createPost(1001L, "delete-post", "删除测试", "daily", null)
                .andExpect(status().isOk())).at("/data/id").asText();
        mockMvc.perform(delete("/api/posts/{id}", postId).with(token(1002L)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/posts/{id}", postId).with(token(1001L))).andExpect(status().isOk());
        mockMvc.perform(delete("/api/posts/{id}", postId).with(token(1001L))).andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM post WHERE business_key = ?", String.class, postId)).isEqualTo("deleted");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted FROM post WHERE business_key = ?", Integer.class, postId)).isEqualTo(1);
        mockMvc.perform(get("/api/posts/{id}", postId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/me/posts").with(token(1001L)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(put("/api/posts/{id}/like", postId).with(token(1002L)))
                .andExpect(status().isNotFound());
        ObjectNode comment = commentBody("after-delete", "不能评论");
        mockMvc.perform(post("/api/posts/{id}/comments", postId).with(token(1002L))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(comment)))
                .andExpect(status().isNotFound());
    }

    @Test
    void likeAndUnlikeAreIdempotentAndReturnAuthoritativeCounts() throws Exception {
        String postId = responseJson(createPost(1001L, "likes", "点赞测试", "daily", null)
                .andExpect(status().isOk())).at("/data/id").asText();
        mockMvc.perform(put("/api/posts/{id}/like", postId).with(token(1002L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.likeCount").value(1));
        mockMvc.perform(put("/api/posts/{id}/like", postId).with(token(1002L)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.likeCount").value(1));
        mockMvc.perform(get("/api/posts/{id}", postId).with(token(1002L)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.liked").value(true));
        mockMvc.perform(get("/api/posts/{id}", postId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.liked").value(false));

        mockMvc.perform(delete("/api/posts/{id}/like", postId).with(token(1002L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(false))
                .andExpect(jsonPath("$.data.likeCount").value(0));
        mockMvc.perform(delete("/api/posts/{id}/like", postId).with(token(1002L)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.likeCount").value(0));
        mockMvc.perform(put("/api/posts/{id}/like", postId).with(token(1002L)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.likeCount").value(1));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_like WHERE user_id = 1002", Long.class)).isEqualTo(1);
    }

    @Test
    void commentsAreIdempotentOrderedAndOnlyCommentAuthorCanDelete() throws Exception {
        String postId = responseJson(createPost(1001L, "comments", "评论测试", "daily", null)
                .andExpect(status().isOk())).at("/data/id").asText();
        String first = responseJson(createComment(postId, 1001L, "comment-1", "第一条")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commentCount").value(1))).at("/data/comment/id").asText();
        String second = responseJson(createComment(postId, 1002L, "comment-2", "第二条")
                .andExpect(status().isOk())).at("/data/comment/id").asText();
        createComment(postId, 1001L, "comment-3", "第三条").andExpect(status().isOk());
        createComment(postId, 1001L, "comment-1", "第一条")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.comment.id").value(first))
                .andExpect(jsonPath("$.data.commentCount").value(3));
        createComment(postId, 1001L, "comment-1", "不同评论")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

        mockMvc.perform(get("/api/posts/{id}/comments", postId).param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.list[0].id").value(first))
                .andExpect(jsonPath("$.data.list[1].id").value(second));
        mockMvc.perform(get("/api/posts/{id}", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commentCount").value(3))
                .andExpect(jsonPath("$.data.comments.length()").value(3));

        mockMvc.perform(delete("/api/posts/{postId}/comments/{commentId}", postId, second).with(token(1001L)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/posts/{postId}/comments/{commentId}", postId, second).with(token(1002L)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.commentCount").value(2));
        mockMvc.perform(delete("/api/posts/{postId}/comments/{commentId}", postId, second).with(token(1002L)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.commentCount").value(2));
        mockMvc.perform(get("/api/posts/{id}/comments", postId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2));

        createComment(postId, 1001L, "blank-comment", " ").andExpect(status().isBadRequest());
        createComment(postId, 1001L, "long-comment", "评".repeat(201)).andExpect(status().isBadRequest());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT comment_count FROM post WHERE business_key = ?", Long.class, postId)).isEqualTo(2);
    }

    @Test
    void publicEndpointsAllowGuestsButWritesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/posts")).andExpect(status().isOk());
        mockMvc.perform(post("/api/posts").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(postBody("guest", "游客不能发布", "daily"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/posts")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/posts").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions createPost(long userId, String requestId, String content, String topic, String relatedTeaId)
            throws Exception {
        ObjectNode request = postBody(requestId, content, topic);
        if (relatedTeaId != null) {
            request.put("relatedTeaId", relatedTeaId);
        }
        return performPost(request, userId);
    }

    private ResultActions performPost(ObjectNode request, long userId) throws Exception {
        return mockMvc.perform(post("/api/posts").with(token(userId)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)));
    }

    private ObjectNode postBody(String requestId, String content, String topic) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("clientRequestId", requestId);
        request.put("content", content);
        request.put("topic", topic);
        request.putArray("tags").add("清香");
        request.putArray("imageMediaIds");
        request.putNull("videoMediaId");
        request.putNull("relatedTeaId");
        return request;
    }

    private ResultActions createComment(String postId, long userId, String requestId, String content) throws Exception {
        return mockMvc.perform(post("/api/posts/{id}/comments", postId).with(token(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(commentBody(requestId, content))));
    }

    private ObjectNode commentBody(String requestId, String content) {
        return objectMapper.createObjectNode().put("clientRequestId", requestId).put("content", content);
    }

    private RequestPostProcessor token(long userId) {
        return request -> {
            request.addHeader("Authorization", "Bearer token-" + userId);
            return request;
        };
    }

    private void insertUser(long id, String role, long points) {
        jdbcTemplate.update("""
                INSERT INTO users (id, business_key, display_name, role, points_balance, status,
                    created_at, updated_at, deleted)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """, id, "user-" + id, "茶友" + id, role, points);
    }

    private void insertSession(long id, long userId, String token) throws Exception {
        LocalDateTime expires = LocalDateTime.now(ZoneOffset.UTC).plusHours(1);
        jdbcTemplate.update("""
                INSERT INTO auth_session (id, user_id, access_token_hash, refresh_token_hash,
                    access_expires_at, refresh_expires_at, created_at, updated_at, deleted)
                VALUES (?, ?, ?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """, id, userId, sha256(token), sha256("refresh-" + token), expires, expires);
    }

    private void insertMedia(long id, String mediaId, long ownerId, String type, String status) {
        jdbcTemplate.update("""
                INSERT INTO media_file (id, media_id, owner_user_id, purpose, media_type, storage_provider,
                    object_key, mime_type, size_bytes, sha256, status, created_at, updated_at, deleted)
                VALUES (?, ?, ?, 'post', ?, 'LOCAL', ?, ?, 100, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """, id, mediaId, ownerId, type, "object/" + mediaId,
                "VIDEO".equals(type) ? "video/mp4" : "image/webp", "a".repeat(64), status);
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private JsonNode responseJson(ResultActions actions) throws Exception {
        MvcResult result = actions.andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
