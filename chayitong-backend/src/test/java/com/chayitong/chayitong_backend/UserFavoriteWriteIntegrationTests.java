package com.chayitong.chayitong_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

// 无测试外层事务：验证真实提交及重复收藏后事务仍能正常结束。
// 只创建临时用户和茶叶，测试结束后按这些编号清理。
@SpringBootTest
// SQL 和事务业务测试；完整认证和归属规则由 SessionSecurityIntegrationTests 验证。
@AutoConfigureMockMvc(addFilters = false)
@EnabledIfSystemProperty(named = "db.integration", matches = "true")
class UserFavoriteWriteIntegrationTests {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private final List<String> createdUsers = new ArrayList<>();
  private final List<String> createdTeas = new ArrayList<>();
  private String userId;
  private String otherUserId;
  private String teaId;
  private String otherTeaId;

  @BeforeEach
  void createTemporaryUsersAndTeas() {
    String suffix = UUID.randomUUID().toString().replace("-", "");
    userId = "fw_" + suffix;
    otherUserId = "fo_" + suffix;
    teaId = "fw_" + suffix.substring(0, 14) + "1";
    otherTeaId = "fw_" + suffix.substring(0, 14) + "2";
    for (String id : new String[] {userId, otherUserId}) {
      jdbcTemplate.update("INSERT INTO users (id, display_name) VALUES (?, ?)", id, "收藏写入测试用户");
      createdUsers.add(id);
    }
    for (String id : new String[] {teaId, otherTeaId}) {
      jdbcTemplate.update("INSERT INTO teas (id, name, type) VALUES (?, ?, ?)", id, "收藏写入测试茶", "绿茶");
      createdTeas.add(id);
    }
    jdbcTemplate.update("INSERT INTO tea_favorites (user_id, tea_id) VALUES (?, ?)", otherUserId, teaId);
  }

  @AfterEach
  void removeTemporaryData() {
    for (String id : createdUsers) {
      jdbcTemplate.update("DELETE FROM users WHERE id = ?", id);
    }
    for (String id : createdTeas) {
      jdbcTemplate.update("DELETE FROM teas WHERE id = ?", id);
    }
  }

  private int favoriteCount(String owner, String tea) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM tea_favorites WHERE user_id = ? AND tea_id = ?",
        Integer.class, owner, tea);
  }

  @Test
  void firstPutCreatesAndRepeatedPutDoesNotDuplicate() throws Exception {
    mockMvc.perform(put("/api/users/{userId}/favorites/{teaId}", userId, teaId))
        .andExpect(status().isCreated())
        .andExpect(content().string(""));
    mockMvc.perform(put("/api/users/{userId}/favorites/{teaId}", userId, teaId))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    assertThat(favoriteCount(userId, teaId)).isEqualTo(1);
    assertThat(favoriteCount(otherUserId, teaId)).isEqualTo(1);
    mockMvc.perform(get("/api/users/{userId}/favorites", userId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.favorites.total").value(1))
        .andExpect(jsonPath("$.favorites.items[0].id").value(teaId));
  }

  @Test
  void cancelOnlyRemovesTheRequestedRelationship() throws Exception {
    mockMvc.perform(put("/api/users/{userId}/favorites/{teaId}", userId, teaId))
        .andExpect(status().isCreated());
    mockMvc.perform(delete("/api/users/{userId}/favorites/{teaId}", userId, teaId))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    assertThat(favoriteCount(userId, teaId)).isZero();
    assertThat(favoriteCount(otherUserId, teaId)).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, userId))
        .isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM teas WHERE id = ?", Integer.class, teaId))
        .isEqualTo(1);
  }

  @Test
  void repeatedCancelSucceedsWhenTheFavoriteIsAlreadyAbsent() throws Exception {
    for (int attempt = 0; attempt < 2; attempt++) {
      mockMvc.perform(delete("/api/users/{userId}/favorites/{teaId}", userId, teaId))
          .andExpect(status().isNoContent())
          .andExpect(content().string(""));
    }
    assertThat(favoriteCount(otherUserId, teaId)).isEqualTo(1);
  }

  @Test
  void unknownUserReturns404ForBothOperations() throws Exception {
    String missingUser = "missing_" + userId;
    mockMvc.perform(put("/api/users/{userId}/favorites/{teaId}", missingUser, teaId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("HTTP_404"));
    mockMvc.perform(delete("/api/users/{userId}/favorites/{teaId}", missingUser, teaId))
        .andExpect(status().isNotFound());
    assertThat(favoriteCount(otherUserId, teaId)).isEqualTo(1);
  }

  @Test
  void unknownTeaCannotBeAddedButItsAbsentFavoriteCanBeCancelled() throws Exception {
    String missingTea = "missing_" + UUID.randomUUID().toString().substring(0, 8);
    mockMvc.perform(put("/api/users/{userId}/favorites/{teaId}", userId, missingTea))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("HTTP_404"));
    mockMvc.perform(delete("/api/users/{userId}/favorites/{teaId}", userId, missingTea))
        .andExpect(status().isNoContent());
    assertThat(favoriteCount(userId, missingTea)).isZero();
  }

  @Test
  void specialInputIsTreatedAsAnIdAndCannotRemoveOtherFavorites() throws Exception {
    String specialId = "' OR 1=1 -- ";
    mockMvc.perform(put("/api/users/{userId}/favorites/{teaId}", userId, specialId))
        .andExpect(status().isNotFound());
    mockMvc.perform(delete("/api/users/{userId}/favorites/{teaId}", userId, specialId))
        .andExpect(status().isNoContent());
    assertThat(favoriteCount(otherUserId, teaId)).isEqualTo(1);
  }
}
