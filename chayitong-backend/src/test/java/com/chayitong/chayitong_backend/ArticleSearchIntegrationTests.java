package com.chayitong.chayitong_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfSystemProperty(named = "db.integration", matches = "true")
class ArticleSearchIntegrationTests {
  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String idPrefix;
  private String marker;
  private String sharedKeyword;
  private String category;
  private String otherCategory;

  @BeforeEach
  void createTemporaryArticles() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    idPrefix = "as_" + suffix + "_";
    marker = "搜索_" + suffix;
    sharedKeyword = marker + "共同词";
    category = "ac_" + suffix;
    otherCategory = "ao_" + suffix;
    insert(1, sharedKeyword + "标题一", "普通摘要", category, true);
    insert(2, "普通标题二", sharedKeyword + "摘要二", category, true);
    insert(3, sharedKeyword + "其他分类标题", "普通摘要", otherCategory, true);
    insert(4, "草稿标题", sharedKeyword + "草稿摘要", category, false);
    insert(5, sharedKeyword + "其他分类草稿", sharedKeyword + "草稿摘要", otherCategory, false);
    insert(6, "其他分类普通标题", sharedKeyword + "其他分类摘要", otherCategory, true);
    String[] specialNames = {"100%特制", "100X特制", "A_B", "AXB", "好!茶", "好茶", "茶'香", "好\\茶"};
    for (int index = 0; index < specialNames.length; index++) {
      insert(index + 7, marker + specialNames[index], "普通摘要", category, true);
    }
    insert(15, "正文才有关键词的文章", "普通摘要", category, true);
  }

  private String id(int index) {
    return idPrefix + String.format("%02d", index);
  }

  private void insert(int index, String title, String summary, String articleCategory, boolean published) {
    jdbcTemplate.update(
        """
        INSERT INTO articles (id, title, summary, category, content_blocks, status, published_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        id(index), title, summary, articleCategory,
        "[{\"type\":\"paragraph\",\"text\":\"" + marker + "仅在正文\"}]",
        published ? "published" : "draft", published ? LocalDateTime.of(2026, 11, 1, 9, 0) : null);
  }

  @AfterTransaction
  void temporaryArticlesWereRolledBack() {
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM articles WHERE category IN (?, ?)",
        Integer.class, category, otherCategory)).isZero();
  }

  @Test
  void anonymousSearchMatchesEitherTitleOrSummaryButNeverDrafts() throws Exception {
    mockMvc.perform(get("/api/articles").param("keyword", sharedKeyword))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(4))
        .andExpect(jsonPath("$.items.length()").value(4))
        .andExpect(jsonPath("$.items[0].id").value(id(1)))
        .andExpect(jsonPath("$.items[1].id").value(id(2)))
        .andExpect(jsonPath("$.items[2].id").value(id(3)))
        .andExpect(jsonPath("$.items[3].id").value(id(6)))
        .andExpect(jsonPath("$.items[0].contentBlocks").doesNotExist());
  }

  @Test
  void categoryAndKeywordApplyTogetherToBothTheTotalAndEveryPage() throws Exception {
    for (int page = 1; page <= 2; page++) {
      mockMvc.perform(get("/api/articles").param("category", category).param("keyword", sharedKeyword)
              .param("page", String.valueOf(page)).param("pageSize", "1").param("status", "draft"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.total").value(2))
          .andExpect(jsonPath("$.items.length()").value(1))
          .andExpect(jsonPath("$.items[0].id").value(id(page)));
    }
    mockMvc.perform(get("/api/articles").param("category", category).param("keyword", sharedKeyword)
            .param("page", "3").param("pageSize", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.items.length()").value(0));
  }

  @Test
  void categoryAloneAndBlankKeywordsKeepAllPublishedArticlesInThatCategory() throws Exception {
    mockMvc.perform(get("/api/articles").param("category", category))
        .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(11));
    for (String keyword : new String[] {"", "   ", "\u3000"}) {
      mockMvc.perform(get("/api/articles").param("category", category).param("keyword", keyword))
          .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(11));
    }
  }

  @Test
  void surroundingWhitespaceIsRemovedAndBlankCategoriesDisableCategoryFiltering() throws Exception {
    mockMvc.perform(get("/api/articles").param("category", "  " + category + "  ")
            .param("keyword", "  " + sharedKeyword + "\u3000"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2));
    mockMvc.perform(get("/api/articles").param("category", "   ").param("keyword", sharedKeyword))
        .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(4));
  }

  @Test
  void wildcardAndEscapeCharactersAreSearchedLiterally() throws Exception {
    String[][] examples = {{"100%", "7"}, {"A_", "9"}, {"好!", "11"}, {"茶'", "13"}, {"好\\", "14"}};
    for (String[] example : examples) {
      mockMvc.perform(get("/api/articles").param("category", category).param("keyword", marker + example[0]))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.total").value(1))
          .andExpect(jsonPath("$.items.length()").value(1))
          .andExpect(jsonPath("$.items[0].id").value(id(Integer.parseInt(example[1]))));
    }
  }

  @Test
  void sqlLikeInputAndMissingMatchesReturnAnEmptyPage() throws Exception {
    for (String keyword : new String[] {"' OR 1=1 -- ", marker + "不存在"}) {
      mockMvc.perform(get("/api/articles").param("category", category).param("keyword", keyword))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.total").value(0))
          .andExpect(jsonPath("$.items.length()").value(0));
    }
    mockMvc.perform(get("/api/articles").param("category", "' OR 1=1 -- ").param("keyword", sharedKeyword))
        .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
  }

  @Test
  void thisStepDoesNotSearchTheBodyOrCategoryName() throws Exception {
    for (String keyword : new String[] {marker + "仅在正文", category}) {
      mockMvc.perform(get("/api/articles").param("keyword", keyword))
          .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }
  }

  @Test
  void filterLengthsAndPaginationAreValidated() throws Exception {
    for (String[] parameter : new String[][] {
        {"category", "c".repeat(33)}, {"keyword", "词".repeat(51)}, {"page", "0"}, {"pageSize", "101"}}) {
      mockMvc.perform(get("/api/articles").param(parameter[0], parameter[1]))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.fieldErrors." + parameter[0]).exists());
    }
    mockMvc.perform(get("/api/articles").param("category", "c".repeat(32)).param("keyword", "词".repeat(50)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
  }
}
