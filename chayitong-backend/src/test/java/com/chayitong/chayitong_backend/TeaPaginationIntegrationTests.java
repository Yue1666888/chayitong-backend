package com.chayitong.chayitong_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// 在本地 MySQL 验证分页；所有临时数据在每个测试后回滚。
@SpringBootTest
// SQL 和事务业务测试；完整认证和归属规则由 SessionSecurityIntegrationTests 验证。
@AutoConfigureMockMvc(addFilters = false)
@Transactional
@EnabledIfSystemProperty(named = "db.integration", matches = "true")
class TeaPaginationIntegrationTests {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private String idPrefix;
  private String testType;

  @BeforeEach
  void createFiveTemporaryTeas() throws Exception {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    idPrefix = "pg_" + suffix + "_";
    testType = "分页_" + suffix;
    for (int index = 1; index <= 5; index++) {
      mockMvc.perform(post("/api/teas")
              .contentType(MediaType.APPLICATION_JSON)
              .content("""
                  {"id":"%s","name":"分页测试茶%d","type":"%s"}
                  """.formatted(idPrefix + index, index, testType)))
          .andExpect(status().isCreated());
    }
  }

  @AfterTransaction
  void verifyTemporaryDataWasRolledBack() {
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM teas WHERE type = ?", Integer.class, testType)).isZero();
  }

  @Test
  void returnsDefaultPaginationAndMatchingTotal() throws Exception {
    mockMvc.perform(get("/api/teas").param("type", testType))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.pageSize").value(10))
        .andExpect(jsonPath("$.total").value(5))
        .andExpect(jsonPath("$.items.length()").value(5));
  }

  @Test
  void returnsStableFirstMiddleAndLastPages() throws Exception {
    for (int page = 1; page <= 3; page++) {
      int expectedSize = page == 3 ? 1 : 2;
      int firstIndex = (page - 1) * 2 + 1;
      mockMvc.perform(get("/api/teas")
              .param("type", testType)
              .param("page", String.valueOf(page))
              .param("pageSize", "2"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.page").value(page))
          .andExpect(jsonPath("$.pageSize").value(2))
          .andExpect(jsonPath("$.total").value(5))
          .andExpect(jsonPath("$.items.length()").value(expectedSize))
          .andExpect(jsonPath("$.items[0].id").value(idPrefix + firstIndex));
    }
  }

  @Test
  void returnsAnEmptyPageWithoutLosingTheMatchingTotal() throws Exception {
    for (String page : new String[] {"4", "2147483647"}) {
      mockMvc.perform(get("/api/teas")
              .param("type", testType).param("page", page).param("pageSize", "2"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.total").value(5))
          .andExpect(jsonPath("$.items.length()").value(0));
    }
  }

  @Test
  void countsAllRowsWhenTheTypeFilterIsAbsentOrBlank() throws Exception {
    int total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM teas", Integer.class);
    mockMvc.perform(get("/api/teas").param("pageSize", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(total))
        .andExpect(jsonPath("$.items.length()").value(2));

    mockMvc.perform(get("/api/teas").param("type", "   ").param("pageSize", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(total))
        .andExpect(jsonPath("$.items.length()").value(2));
  }

  @Test
  void returnsZeroTotalForNoMatchAndTreatsSpecialInputAsAValue() throws Exception {
    for (String type : new String[] {"empty_" + testType, "' OR 1=1 -- "}) {
      mockMvc.perform(get("/api/teas").param("type", type))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.total").value(0))
          .andExpect(jsonPath("$.items.length()").value(0));
    }
  }

  @Test
  void rejectsInvalidPaginationWithFieldErrors() throws Exception {
    String[][] invalidParameters = {
        {"page", "0"}, {"page", "-1"}, {"page", "abc"},
        {"page", "2147483648"}, {"page", ""},
        {"pageSize", "0"}, {"pageSize", "-1"}, {"pageSize", "101"},
        {"pageSize", "abc"}, {"pageSize", "2147483648"}
    };
    for (String[] parameter : invalidParameters) {
      mockMvc.perform(get("/api/teas").param("type", testType)
              .param(parameter[0], parameter[1]))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.fieldErrors." + parameter[0]).exists());
    }
  }
}
