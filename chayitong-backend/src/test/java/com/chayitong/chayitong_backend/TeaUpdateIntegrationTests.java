package com.chayitong.chayitong_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

// 显式运行：mvn -Ddb.integration=true test。每个测试结束后回滚。
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfSystemProperty(named = "db.integration", matches = "true")
class TeaUpdateIntegrationTests {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private String testId;

  @BeforeEach
  void createTestTeaWithinTransaction() throws Exception {
    testId = "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    mockMvc.perform(post("/api/teas")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"id":"%s","name":"修改前的茶","type":"绿茶"}
                """.formatted(testId)))
        .andExpect(status().isCreated());
  }

  @AfterTransaction
  void verifyTestDataWasRolledBack() {
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM teas WHERE id = ?", Integer.class, testId)).isZero();
  }

  @Test
  void updatesExistingTeaAndPreservesItsId() throws Exception {
    int originalCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM teas", Integer.class);
    mockMvc.perform(put("/api/teas/{id}", testId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"修改后的'茶","type":"红茶"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(testId))
        .andExpect(jsonPath("$.name").value("修改后的'茶"))
        .andExpect(jsonPath("$.type").value("红茶"));

    assertThat(jdbcTemplate.queryForObject(
        "SELECT name FROM teas WHERE id = ?", String.class, testId)).isEqualTo("修改后的'茶");
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM teas", Integer.class))
        .isEqualTo(originalCount);

    mockMvc.perform(get("/api/teas/{id}", testId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("修改后的'茶"));
  }

  @Test
  void repeatingTheSameUpdateStillSucceeds() throws Exception {
    String body = """
        {"name":"同一款茶","type":"白茶"}
        """;
    for (int attempt = 0; attempt < 2; attempt++) {
      mockMvc.perform(put("/api/teas/{id}", testId)
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(testId))
          .andExpect(jsonPath("$.name").value("同一款茶"));
    }
  }

  @Test
  void missingIdReturns404WithoutCreatingATea() throws Exception {
    String missingId = "missing_" + UUID.randomUUID().toString().substring(0, 8);
    int originalCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM teas", Integer.class);

    mockMvc.perform(put("/api/teas/{id}", missingId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"不应创建的茶","type":"绿茶"}
                """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("HTTP_404"));

    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM teas", Integer.class))
        .isEqualTo(originalCount);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM teas WHERE id = ?", Integer.class, missingId)).isZero();
  }

  @Test
  void invalidFieldsDoNotChangeTheExistingTea() throws Exception {
    String[] invalidBodies = {
        "{}",
        "{\"name\":\"   \",\"type\":\"绿茶\"}",
        "{\"name\":\"测试茶\"}",
        "{\"name\":\"测试茶\",\"type\":\"\"}",
        """
        {"name":"%s","type":"绿茶"}
        """.formatted("茶".repeat(51)),
        """
        {"name":"测试茶","type":"%s"}
        """.formatted("茶".repeat(21))
    };

    for (String body : invalidBodies) {
      mockMvc.perform(put("/api/teas/{id}", testId)
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    assertThat(jdbcTemplate.queryForObject(
        "SELECT name FROM teas WHERE id = ?", String.class, testId)).isEqualTo("修改前的茶");
    assertThat(jdbcTemplate.queryForObject(
        "SELECT type FROM teas WHERE id = ?", String.class, testId)).isEqualTo("绿茶");
  }
}
