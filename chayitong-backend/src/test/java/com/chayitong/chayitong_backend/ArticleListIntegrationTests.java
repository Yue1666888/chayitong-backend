package com.chayitong.chayitong_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// 完整过滤器链，验证访客只能看到已发布摘要；临时文章在事务结束时回滚。
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfSystemProperty(named = "db.integration", matches = "true")
class ArticleListIntegrationTests {
  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String idPrefix;
  private String category;
  private LocalDateTime newestTime;
  private long expectedTotal;

  @BeforeEach
  void createTemporaryArticles() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    idPrefix = "article_test_" + suffix + "_";
    category = "article_test_" + suffix;
    long initialTotal = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM articles WHERE status = 'published'", Long.class);
    LocalDateTime latest = jdbcTemplate.queryForObject("SELECT MAX(published_at) FROM articles",
        (resultSet, rowNumber) -> resultSet.getObject(1, LocalDateTime.class));
    // 排在已有记录之前，不改动你手工录入的文章。
    newestTime = latest == null ? LocalDateTime.of(2026, 11, 1, 12, 0) : latest.plusDays(1);
    for (int index = 1; index <= 4; index++) {
      LocalDateTime time = index <= 2 ? newestTime : newestTime.minusHours(1);
      if (index == 4) {
        time = newestTime.plusDays(1);
      }
      jdbcTemplate.update(
          """
          INSERT INTO articles (id, title, summary, category, cover, content_blocks, status, published_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?)
          """,
          idPrefix + index, "文章列表测试" + index, "测试摘要" + index, category,
          index == 1 ? "https://example.com/article-test.jpg" : null,
          "[{\"type\":\"paragraph\",\"text\":\"整篇正文不应出现在列表中\"}]",
          index == 4 ? "draft" : "published", time);
    }
    expectedTotal = initialTotal + 3;
  }

  @AfterTransaction
  void temporaryArticlesWereRolledBack() {
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM articles WHERE category = ?", Integer.class, category)).isZero();
  }

  @Test
  void anonymousListReturnsOnlyPublishedSummariesInStableOrder() throws Exception {
    mockMvc.perform(get("/api/articles").param("pageSize", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(expectedTotal))
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].id").value(idPrefix + "1"))
        .andExpect(jsonPath("$.items[1].id").value(idPrefix + "2"))
        .andExpect(jsonPath("$.items[0].title").value("文章列表测试1"))
        .andExpect(jsonPath("$.items[0].summary").value("测试摘要1"))
        .andExpect(jsonPath("$.items[0].category").value(category))
        .andExpect(jsonPath("$.items[0].cover").value("https://example.com/article-test.jpg"))
        .andExpect(jsonPath("$.items[1].cover").value(nullValue()))
        .andExpect(jsonPath("$.items[0].publishedAt")
            .value(newestTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
        .andExpect(jsonPath("$.items[0].contentBlocks").doesNotExist())
        .andExpect(jsonPath("$.items[0].content_blocks").doesNotExist());
    mockMvc.perform(get("/api/articles").param("page", "2").param("pageSize", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(expectedTotal))
        .andExpect(jsonPath("$.items[0].id").value(idPrefix + "3"));
  }

  @Test
  void aClientCannotRequestDraftsByChangingTheStatusParameter() throws Exception {
    mockMvc.perform(get("/api/articles").param("status", "draft").param("pageSize", "100"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(expectedTotal))
        .andExpect(jsonPath("$.items[*].id").value(not(hasItem(idPrefix + "4"))));
  }

  @Test
  void defaultsAndHeadRequestsWorkWithoutLogin() throws Exception {
    mockMvc.perform(get("/api/articles"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.pageSize").value(10))
        .andExpect(jsonPath("$.total").value(expectedTotal));
    mockMvc.perform(head("/api/articles")).andExpect(status().isOk());
  }

  @Test
  void anOutOfRangePagePreservesTheTotal() throws Exception {
    mockMvc.perform(get("/api/articles").param("page", "2147483647").param("pageSize", "100"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(expectedTotal))
        .andExpect(jsonPath("$.items.length()").value(0));
  }

  @Test
  void invalidPaginationReturnsFieldErrors() throws Exception {
    for (String[] parameter : new String[][] {
        {"page", "0"}, {"page", "abc"}, {"page", "2147483648"}, {"pageSize", "0"}, {"pageSize", "101"}}) {
      mockMvc.perform(get("/api/articles").param(parameter[0], parameter[1]))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.fieldErrors." + parameter[0]).exists());
    }
  }
}
