package com.chayitong.chayitong_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

// 显式运行：mvn -Ddb.integration=true test。需要本地 MySQL 和 teas 表。
// MockMvc 与测试使用同一线程；每个测试完成后回滚，不保留测试数据。
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfSystemProperty(named = "db.integration", matches = "true")
class TeaCreationIntegrationTests {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private String testId;

  @BeforeEach
  void generateTestId() {
    testId = "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
  }

  @AfterTransaction
  void verifyTestDataWasRolledBack() {
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM teas WHERE id = ?", Integer.class, testId)).isZero();
  }

  @Test
  void createsTeaAndMakesItAvailableThroughDetail() throws Exception {
    mockMvc.perform(post("/api/teas")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"id":"%s","name":"测试'茶","type":"绿茶"}
                """.formatted(testId)))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/teas/" + testId))
        .andExpect(jsonPath("$.id").value(testId))
        .andExpect(jsonPath("$.name").value("测试'茶"));

    assertThat(jdbcTemplate.queryForObject(
        "SELECT name FROM teas WHERE id = ?", String.class, testId)).isEqualTo("测试'茶");

    mockMvc.perform(get("/api/teas/{id}", testId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("测试'茶"))
        .andExpect(jsonPath("$.type").value("绿茶"));
  }

  @Test
  void duplicateIdReturnsConflictWithoutOverwritingExistingTea() throws Exception {
    String body = """
        {"id":"%s","name":"原来的茶","type":"绿茶"}
        """.formatted(testId);
    mockMvc.perform(post("/api/teas").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/teas")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"id":"%s","name":"另一个名字","type":"红茶"}
                """.formatted(testId)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DATA_CONFLICT"));

    assertThat(jdbcTemplate.queryForObject(
        "SELECT name FROM teas WHERE id = ?", String.class, testId)).isEqualTo("原来的茶");
  }

  @Test
  void invalidFieldsAreRejectedBeforeWritingToDatabase() throws Exception {
    int initialCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM teas", Integer.class);
    String[] invalidBodies = {
        """ 
        {"name":"测试茶","type":"绿茶"}
        """,
        """
        {"id":"bad/id","name":"测试茶","type":"绿茶"}
        """,
        """
        {"id":"%s","name":"   ","type":"绿茶"}
        """.formatted(testId),
        """
        {"id":"%s","name":"测试茶","type":""}
        """.formatted(testId),
        """
        {"id":"%s","name":"测试茶","type":"绿茶"}
        """.formatted("x".repeat(21)),
        """
        {"id":"%s","name":"%s","type":"绿茶"}
        """.formatted(testId, "茶".repeat(51)),
        """
        {"id":"%s","name":"测试茶","type":"%s"}
        """.formatted(testId, "茶".repeat(21))
    };

    for (String body : invalidBodies) {
      mockMvc.perform(post("/api/teas").contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM teas", Integer.class))
        .isEqualTo(initialCount);
  }
}
