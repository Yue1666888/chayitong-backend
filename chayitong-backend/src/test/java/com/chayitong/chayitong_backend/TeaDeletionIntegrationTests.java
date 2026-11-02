package com.chayitong.chayitong_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

// 显式运行：mvn -Ddb.integration=true test。测试只操作事务内的临时茶叶。
@SpringBootTest
// SQL 和事务业务测试；完整认证和归属规则由 SessionSecurityIntegrationTests 验证。
@AutoConfigureMockMvc(addFilters = false)
@Transactional
@EnabledIfSystemProperty(named = "db.integration", matches = "true")
class TeaDeletionIntegrationTests {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private String testId;
  private String otherId;

  @BeforeEach
  void createTemporaryTeasWithinTransaction() throws Exception {
    testId = "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    otherId = "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    for (String id : new String[] {testId, otherId}) {
      mockMvc.perform(post("/api/teas")
              .contentType(MediaType.APPLICATION_JSON)
              .content("""
                  {"id":"%s","name":"删除测试茶","type":"绿茶"}
                  """.formatted(id)))
          .andExpect(status().isCreated());
    }
  }

  @AfterTransaction
  void verifyTemporaryDataWasRolledBack() {
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM teas WHERE id IN (?, ?)",
        Integer.class, testId, otherId)).isZero();
  }

  @Test
  void deletesOnlyTheRequestedTeaAndReturnsEmpty204() throws Exception {
    int originalCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM teas", Integer.class);

    mockMvc.perform(delete("/api/teas/{id}", testId))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM teas WHERE id = ?", Integer.class, testId)).isZero();
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM teas", Integer.class))
        .isEqualTo(originalCount - 1);

    mockMvc.perform(get("/api/teas/{id}", testId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("HTTP_404"));
    mockMvc.perform(get("/api/teas/{id}", otherId))
        .andExpect(status().isOk());
  }

  @Test
  void missingIdReturns404WithoutDeletingOtherTeas() throws Exception {
    int originalCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM teas", Integer.class);
    String missingId = "missing_" + UUID.randomUUID().toString().substring(0, 8);

    mockMvc.perform(delete("/api/teas/{id}", missingId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("HTTP_404"));

    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM teas", Integer.class))
        .isEqualTo(originalCount);
  }

  @Test
  void repeatedDeleteReturns404AfterTheFirstSuccess() throws Exception {
    mockMvc.perform(delete("/api/teas/{id}", testId))
        .andExpect(status().isNoContent());
    mockMvc.perform(delete("/api/teas/{id}", testId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("HTTP_404"));
  }

  @Test
  void specialInputCannotDeleteUnrelatedRows() throws Exception {
    int originalCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM teas", Integer.class);

    mockMvc.perform(delete("/api/teas/{id}", "' OR 1=1 -- "))
        .andExpect(status().isNotFound());

    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM teas", Integer.class))
        .isEqualTo(originalCount);
  }
}
