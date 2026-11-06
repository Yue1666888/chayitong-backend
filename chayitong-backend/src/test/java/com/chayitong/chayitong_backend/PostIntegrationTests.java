package com.chayitong.chayitong_backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "db.integration", matches = "true")
class PostIntegrationTests {
  private static final String PATH = "/api/posts";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void anotherUserCanReadTheCommittedPost() throws Exception {
    String authorId = "it_" + UUID.randomUUID();
    LoginUser author = new LoginUser(authorId, "发帖者", "post-author", "", "USER");
    LoginUser reader = new LoginUser("reader", "读者", "post-reader", "", "USER");
    jdbcTemplate.update("INSERT INTO users (id, display_name) VALUES (?, ?)", authorId, "发帖者");
    try {
      mockMvc.perform(post(PATH).with(user(author)).with(csrf())
              .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"今天喝西湖龙井。\"}"))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.authorId").value(authorId))
          .andExpect(jsonPath("$.createdAt").isString());

      mockMvc.perform(get(PATH).param("pageSize", "1").with(user(reader)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.items[0].authorId").value(authorId))
          .andExpect(jsonPath("$.items[0].content").value("今天喝西湖龙井。"));
    } finally {
      jdbcTemplate.update("DELETE FROM posts WHERE author_id = ?", authorId);
      jdbcTemplate.update("DELETE FROM users WHERE id = ?", authorId);
    }
  }

  @Test
  void requiresLoginAndCsrfToPublishButAllowsPublicReads() throws Exception {
    LoginUser author = new LoginUser("author", "发帖者", "post-author", "", "USER");
    mockMvc.perform(post(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"content\":\"这是一条测试帖\"}"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(post(PATH).with(user(author)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"content\":\"这是一条测试帖\"}"))
        .andExpect(status().isForbidden());
    mockMvc.perform(get(PATH))
        .andExpect(status().isOk());
  }
}
