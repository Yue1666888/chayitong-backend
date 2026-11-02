package com.chayitong.chayitong_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

// 启用完整安全过滤器链，验证访客可以搜索；临时数据随测试事务回滚。
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfSystemProperty(named = "db.integration", matches = "true")
class TeaSearchIntegrationTests {
  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String idPrefix;
  private String marker;
  private String testType;
  private String otherType;

  @BeforeEach
  void createTemporarySearchData() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    idPrefix = "sr_" + suffix + "_";
    marker = "search_" + suffix;
    testType = "搜索_" + suffix;
    otherType = "另一类_" + suffix;
    String[] names = {"龙井一号", "龙井二号", "龙井红茶", "碧螺春",
        "100%特制", "100X特制", "A_B", "AXB", "好!茶", "好茶", "好\\茶", "茶'香"};
    for (int index = 0; index < names.length; index++) {
      jdbcTemplate.update("INSERT INTO teas (id, name, type) VALUES (?, ?, ?)",
          idPrefix + String.format("%02d", index + 1), marker + names[index], index == 2 ? otherType : testType);
    }
  }

  @AfterTransaction
  void temporarySearchDataWasRolledBack() {
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM teas WHERE type IN (?, ?)", Integer.class, testType, otherType)).isZero();
  }

  @Test
  void anonymousKeywordSearchMatchesTeaNamesAcrossTypes() throws Exception {
    mockMvc.perform(get("/api/teas").param("keyword", marker + "龙井"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.total").value(3))
        .andExpect(jsonPath("$.items.length()").value(3))
        .andExpect(jsonPath("$.items[0].id").value(idPrefix + "01"))
        .andExpect(jsonPath("$.items[2].type").value(otherType));
  }

  @Test
  void combinesKeywordTypeAndPaginationWithTheSameTotal() throws Exception {
    mockMvc.perform(get("/api/teas").param("keyword", marker + "龙井").param("type", testType)
            .param("page", "2").param("pageSize", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(2))
        .andExpect(jsonPath("$.pageSize").value(1))
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(idPrefix + "02"));
    mockMvc.perform(get("/api/teas").param("keyword", marker + "龙井").param("type", testType)
            .param("page", "3").param("pageSize", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.items.length()").value(0));
  }

  @Test
  void absentEmptyAndBlankKeywordsPreserveTypeFiltering() throws Exception {
    mockMvc.perform(get("/api/teas").param("type", testType))
        .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(11));
    for (String keyword : new String[] {"", "   ", "\u3000"}) {
      mockMvc.perform(get("/api/teas").param("type", testType).param("keyword", keyword))
          .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(11));
    }
  }

  @Test
  void removesSurroundingWhitespaceAndAllowsBlankType() throws Exception {
    mockMvc.perform(get("/api/teas").param("keyword", "  " + marker + "龙井\u3000")
            .param("type", "   "))
        .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3));
  }

  @Test
  void wildcardCharactersAndQuotesAreSearchedLiterally() throws Exception {
    String[][] examples = {{"100%", "05"}, {"A_", "07"}, {"好!", "09"}, {"好\\", "11"}, {"茶'", "12"}};
    for (String[] example : examples) {
      mockMvc.perform(get("/api/teas").param("keyword", marker + example[0]).param("type", testType))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.total").value(1))
          .andExpect(jsonPath("$.items.length()").value(1))
          .andExpect(jsonPath("$.items[0].id").value(idPrefix + example[1]));
    }
  }

  @Test
  void noMatchAndSqlLikeInputReturnAnEmptyPage() throws Exception {
    for (String keyword : new String[] {marker + "不存在", "' OR 1=1 -- "}) {
      mockMvc.perform(get("/api/teas").param("keyword", keyword).param("type", testType))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.total").value(0))
          .andExpect(jsonPath("$.items.length()").value(0));
    }
    // 当前阶段只搜 name，输入一个仅存在于 type 中的字符串不应匹配。
    mockMvc.perform(get("/api/teas").param("keyword", otherType))
        .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
  }

  @Test
  void validatesKeywordLengthAndStillValidatesPagination() throws Exception {
    mockMvc.perform(get("/api/teas").param("keyword", "字".repeat(51)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors.keyword").exists());
    mockMvc.perform(get("/api/teas").param("keyword", "字".repeat(50)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    mockMvc.perform(get("/api/teas").param("keyword", marker).param("page", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors.page").exists());
  }
}
