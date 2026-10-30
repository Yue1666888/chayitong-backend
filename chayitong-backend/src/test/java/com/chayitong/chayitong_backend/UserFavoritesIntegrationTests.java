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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfSystemProperty(named = "db.integration", matches = "true")
class UserFavoritesIntegrationTests {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private String userId;
  private String emptyUserId;
  private String otherUserId;
  private String teaPrefix;

  @BeforeEach
  void createTemporaryRelations() {
    String suffix = UUID.randomUUID().toString().replace("-", "");
    userId = "uf_" + suffix;
    emptyUserId = "ue_" + suffix;
    otherUserId = "uo_" + suffix;
    teaPrefix = "uf_" + suffix.substring(0, 8) + "_";
    jdbcTemplate.update("INSERT INTO users (id, display_name) VALUES (?, ?)", userId, "收藏测试用户");
    jdbcTemplate.update("INSERT INTO users (id, display_name) VALUES (?, ?)", emptyUserId, "暂无收藏用户");
    jdbcTemplate.update("INSERT INTO users (id, display_name) VALUES (?, ?)", otherUserId, "另一位用户");
    for (int index = 1; index <= 3; index++) {
      jdbcTemplate.update("INSERT INTO teas (id, name, type) VALUES (?, ?, ?)",
          teaPrefix + index, "收藏测试茶" + index, "绿茶");
    }
    jdbcTemplate.update("INSERT INTO tea_favorites (user_id, tea_id) VALUES (?, ?)", userId, teaPrefix + 1);
    jdbcTemplate.update("INSERT INTO tea_favorites (user_id, tea_id) VALUES (?, ?)", userId, teaPrefix + 2);
    jdbcTemplate.update("INSERT INTO tea_favorites (user_id, tea_id) VALUES (?, ?)", otherUserId, teaPrefix + 1);
    jdbcTemplate.update("INSERT INTO tea_favorites (user_id, tea_id) VALUES (?, ?)", otherUserId, teaPrefix + 3);
  }

  @AfterTransaction
  void verifyTemporaryUsersWereRolledBack() {
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM users WHERE id IN (?, ?, ?)",
        Integer.class, userId, emptyUserId, otherUserId)).isZero();
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM teas WHERE id IN (?, ?, ?)",
        Integer.class, teaPrefix + 1, teaPrefix + 2, teaPrefix + 3)).isZero();
  }

  @Test
  void returnsUserDetailsAndJoinedTeaNames() throws Exception {
    mockMvc.perform(get("/api/users/{userId}/favorites", userId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.id").value(userId))
        .andExpect(jsonPath("$.user.displayName").value("收藏测试用户"))
        .andExpect(jsonPath("$.favorites.page").value(1))
        .andExpect(jsonPath("$.favorites.pageSize").value(10))
        .andExpect(jsonPath("$.favorites.total").value(2))
        .andExpect(jsonPath("$.favorites.items.length()").value(2))
        .andExpect(jsonPath("$.favorites.items[0].id").value(teaPrefix + 1))
        .andExpect(jsonPath("$.favorites.items[0].name").value("收藏测试茶1"));
  }

  @Test
  void paginatesOnlyTheRequestedUsersFavorites() throws Exception {
    mockMvc.perform(get("/api/users/{userId}/favorites", userId)
            .param("page", "2").param("pageSize", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.favorites.total").value(2))
        .andExpect(jsonPath("$.favorites.items.length()").value(1))
        .andExpect(jsonPath("$.favorites.items[0].id").value(teaPrefix + 2));

    mockMvc.perform(get("/api/users/{userId}/favorites", otherUserId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.favorites.total").value(2))
        .andExpect(jsonPath("$.favorites.items[0].id").value(teaPrefix + 1))
        .andExpect(jsonPath("$.favorites.items[1].id").value(teaPrefix + 3));
  }

  @Test
  void existingUserWithoutFavoritesReturnsAnEmptyPage() throws Exception {
    mockMvc.perform(get("/api/users/{userId}/favorites", emptyUserId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.id").value(emptyUserId))
        .andExpect(jsonPath("$.favorites.total").value(0))
        .andExpect(jsonPath("$.favorites.items.length()").value(0));
  }

  @Test
  void outOfRangePagePreservesUserAndTotal() throws Exception {
    mockMvc.perform(get("/api/users/{userId}/favorites", userId)
            .param("page", "3").param("pageSize", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.id").value(userId))
        .andExpect(jsonPath("$.favorites.total").value(2))
        .andExpect(jsonPath("$.favorites.items.length()").value(0));
  }

  @Test
  void nonexistentUserAndSpecialInputReturn404() throws Exception {
    for (String id : new String[] {"missing_" + userId, "' OR 1=1 -- "}) {
      mockMvc.perform(get("/api/users/{userId}/favorites", id))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("HTTP_404"));
    }
  }

  @Test
  void invalidPaginationReturnsFieldErrors() throws Exception {
    for (String[] parameter : new String[][] {{"page", "0"}, {"pageSize", "101"}, {"page", "abc"}}) {
      mockMvc.perform(get("/api/users/{userId}/favorites", userId)
              .param(parameter[0], parameter[1]))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.fieldErrors." + parameter[0]).exists());
    }
  }
}
