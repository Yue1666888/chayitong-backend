package com.chayitong.chayitong_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

// 不在测试外层开启事务，以便真正验证 Service 的提交和回滚。
// 所有临时编号均在测试结束后清理，不修改原有业务记录。
@SpringBootTest
// SQL 和事务业务测试；完整认证和归属规则由 SessionSecurityIntegrationTests 验证。
@AutoConfigureMockMvc(addFilters = false)
@EnabledIfSystemProperty(named = "db.integration", matches = "true")
class TeaBatchCreationIntegrationTests {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private String idPrefix;
  private List<String> allocatedIds;

  @BeforeEach
  void prepareTemporaryIds() {
    idPrefix = "bt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    allocatedIds = new ArrayList<>();
  }

  @AfterEach
  void removeTemporaryRecords() {
    for (String id : allocatedIds) {
      jdbcTemplate.update("DELETE FROM teas WHERE id = ?", id);
    }
  }

  private String nextId() {
    String id = idPrefix + "_" + String.format("%02d", allocatedIds.size() + 1);
    allocatedIds.add(id);
    return id;
  }

  private String item(String id, String name) {
    return """
        {"id":"%s","name":"%s","type":"绿茶"}
        """.formatted(id, name);
  }

  private String batch(String... items) {
    return "{\"items\":[" + String.join(",", items) + "]}";
  }

  private int countRows(String id) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM teas WHERE id = ?", Integer.class, id);
  }

  @Test
  void successfulBatchIsCommittedAndCanBeQueried() throws Exception {
    String firstId = nextId();
    String secondId = nextId();
    mockMvc.perform(post("/api/teas/batch").contentType(MediaType.APPLICATION_JSON)
            .content(batch(item(firstId, "第一款批量茶"), item(secondId, "第二款批量茶"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value(firstId))
        .andExpect(jsonPath("$[1].id").value(secondId));

    // 请求已经结束，使用数据库查询确认两条记录都已提交。
    assertThat(countRows(firstId)).isEqualTo(1);
    assertThat(countRows(secondId)).isEqualTo(1);
    mockMvc.perform(get("/api/teas/{id}", secondId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("第二款批量茶"));
  }

  @Test
  void secondInsertFailureRollsBackTheFirstInsert() throws Exception {
    String repeatedId = nextId();
    mockMvc.perform(post("/api/teas/batch").contentType(MediaType.APPLICATION_JSON)
            .content(batch(item(repeatedId, "先插入的茶"), item(repeatedId, "编号重复的茶"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DATA_CONFLICT"));

    // 没有测试外层事务：只有业务方法正确回滚，这里才能查到 0 条。
    assertThat(countRows(repeatedId)).isZero();
  }

  @Test
  void nestedValidationRejectsTheWholeRequestBeforeWriting() throws Exception {
    String firstId = nextId();
    String secondId = nextId();
    mockMvc.perform(post("/api/teas/batch").contentType(MediaType.APPLICATION_JSON)
            .content(batch(item(firstId, "有效茶名"), item(secondId, "   "))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors['items[1].name']").value("茶名不能为空"));

    assertThat(countRows(firstId)).isZero();
    assertThat(countRows(secondId)).isZero();
  }

  @Test
  void rejectsMissingEmptyAndNullItems() throws Exception {
    for (String body : new String[] {"{}", "{\"items\":null}", "{\"items\":[]}", "{\"items\":[null]}"}) {
      mockMvc.perform(post("/api/teas/batch").contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
  }

  @Test
  void acceptsTwentyItems() throws Exception {
    List<String> items = new ArrayList<>();
    for (int index = 0; index < 20; index++) {
      items.add(item(nextId(), "批量边界测试茶"));
    }
    mockMvc.perform(post("/api/teas/batch").contentType(MediaType.APPLICATION_JSON)
            .content(batch(items.toArray(new String[0]))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.length()").value(20));

    for (String id : allocatedIds) {
      assertThat(countRows(id)).isEqualTo(1);
    }
  }

  @Test
  void rejectsTwentyOneItemsWithoutWriting() throws Exception {
    List<String> items = new ArrayList<>();
    for (int index = 0; index < 21; index++) {
      items.add(item(nextId(), "超出上限测试茶"));
    }
    mockMvc.perform(post("/api/teas/batch").contentType(MediaType.APPLICATION_JSON)
            .content(batch(items.toArray(new String[0]))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors.items").exists());

    for (String id : allocatedIds) {
      assertThat(countRows(id)).isZero();
    }
  }
}
