package com.chayitong.chayitong_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfSystemProperty(named = "db.integration", matches = "true")
class ArticleDetailIntegrationTests {
  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ObjectMapper objectMapper;

  private String publishedId;
  private String draftId;
  private String emptyBodyId;
  private String category;
  private final String paragraph = "正文包含\"引号\"、换行\n和反斜线\\。";

  @BeforeEach
  void createTemporaryArticles() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    publishedId = "detail_" + suffix + "_published";
    draftId = "detail_" + suffix + "_draft";
    emptyBodyId = "detail_" + suffix + "_empty";
    category = "detail_" + suffix;
    String body = objectMapper.writeValueAsString(List.of(
        Map.of("type", "heading", "level", 3, "text", "文章标题块"),
        Map.of("type", "paragraph", "text", paragraph),
        Map.of("type", "list", "ordered", false, "items", List.of("第一项", "第二项")),
        Map.of("type", "image", "src", "https://example.com/article.jpg", "alt", "练习图片")));
    insert(publishedId, "published", body, LocalDateTime.of(2026, 11, 1, 9, 0));
    insert(draftId, "draft", "[{\"type\":\"paragraph\",\"text\":\"草稿正文不能公开\"}]", null);
    insert(emptyBodyId, "published", "[]", null);
  }

  private void insert(String id, String articleStatus, String body, LocalDateTime publishedAt) {
    jdbcTemplate.update(
        """
        INSERT INTO articles (id, title, summary, category, cover, content_blocks, status, published_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        id, "详情测试文章", "详情测试摘要", category, null, body, articleStatus, publishedAt);
  }

  @AfterTransaction
  void temporaryArticlesWereRolledBack() {
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM articles WHERE category = ?", Integer.class, category)).isZero();
  }

  @Test
  void anonymousDetailReturnsStructuredContentBlocksAndPreservesTheirValues() throws Exception {
    mockMvc.perform(get("/api/articles/{id}", publishedId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(publishedId))
        .andExpect(jsonPath("$.title").value("详情测试文章"))
        .andExpect(jsonPath("$.summary").value("详情测试摘要"))
        .andExpect(jsonPath("$.category").value(category))
        .andExpect(jsonPath("$.cover").value(nullValue()))
        .andExpect(jsonPath("$.publishedAt").value("2026-11-01T09:00:00"))
        .andExpect(jsonPath("$.contentBlocks").isArray())
        .andExpect(jsonPath("$.contentBlocks.length()").value(4))
        .andExpect(jsonPath("$.contentBlocks[0].type").value("heading"))
        .andExpect(jsonPath("$.contentBlocks[0].level").value(3))
        .andExpect(jsonPath("$.contentBlocks[1].text").value(paragraph))
        .andExpect(jsonPath("$.contentBlocks[2].ordered").value(false))
        .andExpect(jsonPath("$.contentBlocks[2].items[1]").value("第二项"))
        .andExpect(jsonPath("$.contentBlocks[3].src").value("https://example.com/article.jpg"))
        .andExpect(jsonPath("$.content_blocks").doesNotExist());
  }

  @Test
  void draftsMissingIdsAndSqlLikeInputReturnTheSameJson404() throws Exception {
    for (String id : new String[] {draftId, "missing_" + publishedId, "' OR 1=1 -- "}) {
      mockMvc.perform(get("/api/articles/{id}", id).param("status", "published"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("HTTP_404"))
          .andExpect(jsonPath("$.message").value("请求的资源不存在"))
          .andExpect(jsonPath("$.contentBlocks").doesNotExist())
          .andExpect(jsonPath("$.title").doesNotExist());
    }
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void thePublicDetailEndpointDoesNotExposeDraftsToAdministratorsEither() throws Exception {
    // 此处仅模拟管理员身份；完整登录流程由 SessionSecurityIntegrationTests 验证。
    mockMvc.perform(get("/api/articles/{id}", draftId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("HTTP_404"))
        .andExpect(jsonPath("$.contentBlocks").doesNotExist());
  }

  @Test
  void anEmptyBodyRemainsAnArrayAndNullableTimeRemainsNull() throws Exception {
    mockMvc.perform(get("/api/articles/{id}", emptyBodyId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contentBlocks").isArray())
        .andExpect(jsonPath("$.contentBlocks.length()").value(0))
        .andExpect(jsonPath("$.publishedAt").value(nullValue()));
  }

  @Test
  void headRequestsRespectTheSamePublicationRules() throws Exception {
    // MockMvc 不经过真实 Servlet 容器，这里验证 HEAD 的访问规则和发布状态判断。
    mockMvc.perform(head("/api/articles/{id}", publishedId))
        .andExpect(status().isOk());
    mockMvc.perform(head("/api/articles/{id}", draftId)).andExpect(status().isNotFound());
  }
}
