package com.chayitong.chayitong_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

// 不包裹测试事务，验证发布业务实际提交、失败回滚和多连接并发。
// 仅操作随机编号的临时账号/文章，每次测试后清理，不改变已有文章状态。
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "db.integration", matches = "true")
class ArticlePublicationIntegrationTests {
  private static final String TEST_PASSWORD = "Publish-Test-2026!";
  private static final String VALID_BODY = "[{\"type\":\"paragraph\",\"text\":\"发布测试正文\"}]";
  private static final LocalDateTime OLD_TIME = LocalDateTime.of(2020, 1, 1, 9, 0);
  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ArticleService articleService;

  private String adminId;
  private String userId;
  private String adminLogin;
  private String userLogin;
  private String draftId;
  private String publishedId;
  private String category;

  private record CsrfSession(MockHttpSession session, String headerName, String token) { }

  @BeforeEach
  void createTemporaryAccountsAndArticles() {
    String suffix = UUID.randomUUID().toString().replace("-", "");
    adminId = "pub_admin_" + suffix;
    userId = "pub_user_" + suffix;
    adminLogin = "pub_admin_" + suffix;
    userLogin = "pub_user_" + suffix;
    draftId = "pub_" + suffix + "_draft";
    publishedId = "pub_" + suffix + "_published";
    category = "pub_" + suffix.substring(0, 16);
    String hash = passwordEncoder.encode(TEST_PASSWORD);
    for (String[] account : new String[][] {
        {adminId, adminLogin, "ADMIN"}, {userId, userLogin, "USER"}}) {
      jdbcTemplate.update("INSERT INTO users (id, display_name, role) VALUES (?, ?, ?)",
          account[0], "发布测试用户", account[2]);
      jdbcTemplate.update("INSERT INTO user_credentials (user_id, login_name, password_hash) VALUES (?, ?, ?)",
          account[0], account[1], hash);
    }
    for (String id : new String[] {draftId, publishedId}) {
      boolean published = id.equals(publishedId);
      jdbcTemplate.update(
          """
          INSERT INTO articles (id, title, summary, category, content_blocks, status, published_at)
          VALUES (?, ?, ?, ?, ?, ?, ?)
          """,
          id, "发布测试文章", "发布测试摘要", category, VALID_BODY,
          published ? "published" : "draft", published ? OLD_TIME : null);
    }
  }

  @AfterEach
  void removeTemporaryRecords() {
    jdbcTemplate.update("DELETE FROM articles WHERE id IN (?, ?) OR category = ?", draftId, publishedId, category);
    jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", adminId, userId);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM articles WHERE id IN (?, ?)", Integer.class, draftId, publishedId)).isZero();
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM user_credentials WHERE user_id IN (?, ?)", Integer.class, adminId, userId)).isZero();
  }

  private CsrfSession fetchCsrf(MockHttpSession session) throws Exception {
    var request = get("/api/auth/csrf");
    if (session != null) {
      request.session(session);
    }
    MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
    var data = objectMapper.readTree(result.getResponse().getContentAsString());
    return new CsrfSession((MockHttpSession) result.getRequest().getSession(false),
        data.get("headerName").asString(), data.get("token").asString());
  }

  private CsrfSession logIn(String loginName) throws Exception {
    CsrfSession beforeLogin = fetchCsrf(null);
    MvcResult result = mockMvc.perform(post("/api/auth/login").session(beforeLogin.session())
            .header(beforeLogin.headerName(), beforeLogin.token())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("username", loginName).param("password", TEST_PASSWORD))
        .andExpect(status().isOk()).andReturn();
    return fetchCsrf((MockHttpSession) result.getRequest().getSession(false));
  }

  private LocalDateTime storedTime(String id) {
    return jdbcTemplate.queryForObject("SELECT published_at FROM articles WHERE id = ?",
        (resultSet, rowNumber) -> resultSet.getObject(1, LocalDateTime.class), id);
  }

  private void assertDraftUnchanged() {
    assertThat(jdbcTemplate.queryForObject(
        "SELECT status FROM articles WHERE id = ?", String.class, draftId)).isEqualTo("draft");
    assertThat(storedTime(draftId)).isNull();
  }

  @Test
  void administratorPublishesADraftAndTheCommittedArticleBecomesPublic() throws Exception {
    mockMvc.perform(get("/api/articles/{id}", draftId)).andExpect(status().isNotFound());
    CsrfSession csrf = logIn(adminLogin);
    LocalDateTime before = LocalDateTime.now(ZONE).withNano(0);
    mockMvc.perform(put("/api/admin/articles/{id}/publication", draftId).session(csrf.session())
            .header(csrf.headerName(), csrf.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(draftId))
        .andExpect(jsonPath("$.status").value("published"))
        .andExpect(jsonPath("$.publishedAt").isString());
    LocalDateTime time = storedTime(draftId);
    assertThat(time).isBetween(before, LocalDateTime.now(ZONE));
    // 业务事务已结束；匿名请求也能读到这篇刚发布的文章。
    mockMvc.perform(get("/api/articles/{id}", draftId)).andExpect(status().isOk());
    mockMvc.perform(get("/api/articles").param("category", category))
        .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2));
    mockMvc.perform(put("/api/admin/articles/{id}/publication", draftId).session(csrf.session())
            .header(csrf.headerName(), csrf.token())).andExpect(status().isOk());
    assertThat(storedTime(draftId)).isEqualTo(time);
  }

  @Test
  void publishingAnAlreadyPublishedArticlePreservesItsOriginalTime() throws Exception {
    CsrfSession csrf = logIn(adminLogin);
    mockMvc.perform(put("/api/admin/articles/{id}/publication", publishedId).session(csrf.session())
            .header(csrf.headerName(), csrf.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.publishedAt").value("2020-01-01T09:00:00"));
    assertThat(storedTime(publishedId)).isEqualTo(OLD_TIME);
  }

  @Test
  void publishingADraftReplacesAnOldPublicationTime() throws Exception {
    jdbcTemplate.update("UPDATE articles SET published_at = ? WHERE id = ?", OLD_TIME, draftId);
    CsrfSession csrf = logIn(adminLogin);
    mockMvc.perform(put("/api/admin/articles/{id}/publication", draftId).session(csrf.session())
            .header(csrf.headerName(), csrf.token())).andExpect(status().isOk());
    assertThat(storedTime(draftId)).isAfter(OLD_TIME);
  }

  @Test
  void aNormalUserCannotPublishEvenWithValidCsrfAndAnAdminHeader() throws Exception {
    CsrfSession csrf = logIn(userLogin);
    mockMvc.perform(put("/api/admin/articles/{id}/publication", draftId).session(csrf.session())
            .header(csrf.headerName(), csrf.token()).header("X-Role", "ADMIN"))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    assertDraftUnchanged();
  }

  @Test
  void anAnonymousClientCannotPublishEvenWithValidCsrf() throws Exception {
    CsrfSession csrf = fetchCsrf(null);
    mockMvc.perform(put("/api/admin/articles/{id}/publication", draftId).session(csrf.session())
            .header(csrf.headerName(), csrf.token()))
        .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    assertDraftUnchanged();
  }

  @Test
  void administratorsStillNeedValidCsrf() throws Exception {
    CsrfSession csrf = logIn(adminLogin);
    mockMvc.perform(put("/api/admin/articles/{id}/publication", draftId).session(csrf.session()))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_FAILED"));
    mockMvc.perform(put("/api/admin/articles/{id}/publication", draftId).session(csrf.session())
            .header(csrf.headerName(), "incorrect-token"))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_FAILED"));
    assertDraftUnchanged();
  }

  @Test
  void unknownIdsAndSqlLikeInputReturn404() throws Exception {
    CsrfSession csrf = logIn(adminLogin);
    for (String id : new String[] {"missing_" + draftId, "' OR 1=1 -- "}) {
      mockMvc.perform(put("/api/admin/articles/{id}/publication", id).session(csrf.session())
              .header(csrf.headerName(), csrf.token()))
          .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("HTTP_404"));
    }
    assertDraftUnchanged();
  }

  @Test
  void anIncompleteArticleCannotBePublishedAndFailureDoesNotChangeItsState() throws Exception {
    CsrfSession csrf = logIn(adminLogin);
    for (String[] fields : new String[][] {
        {"   ", category, VALID_BODY}, {"有效标题", "   ", VALID_BODY},
        {"有效标题", category, "[]"},
        {"有效标题", category, "[{\"type\":\"paragraph\"}]"},
        {"有效标题", category, "[{\"type\":\"paragraph\",\"text\":123}]"},
        {"有效标题", category, "[{\"type\":\"video\"}]"}}) {
      jdbcTemplate.update("UPDATE articles SET title = ?, category = ?, content_blocks = ? WHERE id = ?",
          fields[0], fields[1], fields[2], draftId);
      mockMvc.perform(put("/api/admin/articles/{id}/publication", draftId).session(csrf.session())
              .header(csrf.headerName(), csrf.token()))
          .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ARTICLE_NOT_READY"));
      assertDraftUnchanged();
    }
  }

  @Test
  void withdrawalHidesAnArticleAndAllowsPublishingAgain() throws Exception {
    CsrfSession csrf = logIn(adminLogin);
    var before = mockMvc.perform(get("/api/articles/{id}", publishedId))
        .andExpect(status().isOk()).andReturn();
    for (int attempt = 0; attempt < 2; attempt++) {
      mockMvc.perform(delete("/api/admin/articles/{id}/publication", publishedId)
              .session(csrf.session()).header(csrf.headerName(), csrf.token()))
          .andExpect(status().isNoContent());
    }
    assertThat(storedTime(publishedId)).isNull();
    mockMvc.perform(get("/api/articles/{id}", publishedId)).andExpect(status().isNotFound());
    mockMvc.perform(get("/api/articles").param("category", category))
        .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));

    mockMvc.perform(put("/api/admin/articles/{id}/publication", publishedId)
            .session(csrf.session()).header(csrf.headerName(), csrf.token()))
        .andExpect(status().isOk());
    assertThat(storedTime(publishedId)).isAfter(OLD_TIME);
    var after = mockMvc.perform(get("/api/articles/{id}", publishedId))
        .andExpect(status().isOk()).andReturn();
    assertThat(objectMapper.readTree(after.getResponse().getContentAsString()).get("contentBlocks"))
        .isEqualTo(objectMapper.readTree(before.getResponse().getContentAsString()).get("contentBlocks"));
  }

  @Test
  void withdrawalCanHideAnArticleWithInvalidStoredContentAndReturns404ForMissingIds() throws Exception {
    jdbcTemplate.update("UPDATE articles SET content_blocks = ? WHERE id = ?", "[null]", publishedId);
    CsrfSession csrf = logIn(adminLogin);
    mockMvc.perform(delete("/api/admin/articles/{id}/publication", publishedId)
            .session(csrf.session()).header(csrf.headerName(), csrf.token()))
        .andExpect(status().isNoContent());
    mockMvc.perform(get("/api/articles/{id}", publishedId)).andExpect(status().isNotFound());
    mockMvc.perform(delete("/api/admin/articles/{id}/publication", "missing_" + publishedId)
            .session(csrf.session()).header(csrf.headerName(), csrf.token()))
        .andExpect(status().isNotFound());
  }

  @Test
  void withdrawalRequiresAdministratorAndCsrf() throws Exception {
    CsrfSession user = logIn(userLogin);
    mockMvc.perform(delete("/api/admin/articles/{id}/publication", publishedId)
            .session(user.session()).header(user.headerName(), user.token()))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    CsrfSession admin = logIn(adminLogin);
    mockMvc.perform(delete("/api/admin/articles/{id}/publication", publishedId).session(admin.session()))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_FAILED"));
    mockMvc.perform(get("/api/articles/{id}", publishedId)).andExpect(status().isOk());
    assertThat(storedTime(publishedId)).isEqualTo(OLD_TIME);
  }

  @Test
  void createsAPrivateDraftWithValidatedBodyAndCanPublishIt() throws Exception {
    CsrfSession csrf = logIn(adminLogin);
    var result = mockMvc.perform(post("/api/admin/articles").session(csrf.session())
            .header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"title":"新建测试文章","category":"%s","contentBlocks":%s,"status":"published"}
                """.formatted(category, VALID_BODY)))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("draft"))
        .andReturn();
    String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString();
    assertThat(UUID.fromString(id).toString()).isEqualTo(id);
    assertThat(storedTime(id)).isNull();
    mockMvc.perform(get("/api/articles/{id}", id)).andExpect(status().isNotFound());
    mockMvc.perform(put("/api/admin/articles/{id}/publication", id).session(csrf.session())
            .header(csrf.headerName(), csrf.token())).andExpect(status().isOk());
    mockMvc.perform(get("/api/articles/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary").value(""))
        .andExpect(jsonPath("$.contentBlocks[0].type").value("paragraph"))
        .andExpect(jsonPath("$.contentBlocks[0].text").value("发布测试正文"));
  }

  @Test
  void rejectsInvalidDraftInputAndAllowsAnEmptyDraftBody() throws Exception {
    CsrfSession csrf = logIn(adminLogin);
    for (String blocks : new String[] {"null", "{}", "[null]", "[{\"type\":\"paragraph\",\"text\":123}]"}) {
      mockMvc.perform(post("/api/admin/articles").session(csrf.session())
              .header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
              .content("""
                  {"title":"新建测试文章","category":"%s","contentBlocks":%s}
                  """.formatted(category, blocks)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.fieldErrors.contentBlocks").exists());
    }
    mockMvc.perform(post("/api/admin/articles").session(csrf.session())
            .header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"title":" ","category":"%s","contentBlocks":[]}
                """.formatted(category)))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.title").exists());
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM articles WHERE category = ?", Integer.class, category)).isEqualTo(2);
    var result = mockMvc.perform(post("/api/admin/articles").session(csrf.session())
            .header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"title":"空正文草稿","category":"%s","contentBlocks":[]}
                """.formatted(category)))
        .andExpect(status().isCreated()).andReturn();
    String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString();
    mockMvc.perform(put("/api/admin/articles/{id}/publication", id).session(csrf.session())
            .header(csrf.headerName(), csrf.token()))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ARTICLE_NOT_READY"));
  }

  @Test
  void draftCreationRequiresAdministratorAndCsrf() throws Exception {
    String body = """
        {"title":"权限测试文章","category":"%s","contentBlocks":[]}
        """.formatted(category);
    CsrfSession user = logIn(userLogin);
    mockMvc.perform(post("/api/admin/articles").session(user.session())
            .header(user.headerName(), user.token()).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    CsrfSession admin = logIn(adminLogin);
    mockMvc.perform(post("/api/admin/articles").session(admin.session())
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_FAILED"));
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM articles WHERE category = ?", Integer.class, category)).isEqualTo(2);
  }

  @Test
  void replacesADraftEvenWhenItsOldBodyIsInvalidAndPublishesTheUpdatedContent() throws Exception {
    jdbcTemplate.update("UPDATE articles SET content_blocks = ? WHERE id = ?", "[null]", draftId);
    CsrfSession csrf = logIn(adminLogin);
    String body = """
        {"title":"修改后的标题","category":"%s","summary":"修改后的摘要",
         "contentBlocks":[{"type":"paragraph","text":"修改后的正文"}]}
        """.formatted(category);
    mockMvc.perform(put("/api/admin/articles/{id}", draftId).session(csrf.session())
            .header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
            .content(body.replace("修改后的标题", " ")))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.title").exists());
    for (int attempt = 0; attempt < 2; attempt++) {
      mockMvc.perform(put("/api/admin/articles/{id}", draftId).session(csrf.session())
              .header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isNoContent());
    }
    assertDraftUnchanged();
    mockMvc.perform(get("/api/articles/{id}", draftId)).andExpect(status().isNotFound());
    mockMvc.perform(get("/api/admin/articles/{id}", draftId).session(csrf.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("draft"))
        .andExpect(jsonPath("$.article.id").value(draftId))
        .andExpect(jsonPath("$.article.title").value("修改后的标题"))
        .andExpect(jsonPath("$.article.publishedAt").value(nullValue()))
        .andExpect(jsonPath("$.article.contentBlocks[0].text").value("修改后的正文"));
    mockMvc.perform(put("/api/admin/articles/{id}/publication", draftId).session(csrf.session())
            .header(csrf.headerName(), csrf.token())).andExpect(status().isOk());
    mockMvc.perform(get("/api/articles/{id}", draftId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("修改后的标题"))
        .andExpect(jsonPath("$.summary").value("修改后的摘要"))
        .andExpect(jsonPath("$.contentBlocks[0].text").value("修改后的正文"));
    mockMvc.perform(delete("/api/admin/articles/{id}/publication", draftId)
            .session(csrf.session()).header(csrf.headerName(), csrf.token()))
        .andExpect(status().isNoContent());
    mockMvc.perform(delete("/api/admin/articles/{id}", draftId)
            .session(csrf.session()).header(csrf.headerName(), csrf.token()))
        .andExpect(status().isNoContent());
    mockMvc.perform(get("/api/admin/articles/{id}", draftId).session(csrf.session()))
        .andExpect(status().isNotFound());
    mockMvc.perform(delete("/api/admin/articles/{id}", draftId)
            .session(csrf.session()).header(csrf.headerName(), csrf.token()))
        .andExpect(status().isNotFound());
  }

  @Test
  void deletingRequiresAdministratorCsrfAndDraftState() throws Exception {
    CsrfSession user = logIn(userLogin);
    mockMvc.perform(delete("/api/admin/articles/{id}", draftId)
            .session(user.session()).header(user.headerName(), user.token()))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    CsrfSession admin = logIn(adminLogin);
    mockMvc.perform(delete("/api/admin/articles/{id}", draftId).session(admin.session()))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_FAILED"));
    mockMvc.perform(delete("/api/admin/articles/{id}", publishedId)
            .session(admin.session()).header(admin.headerName(), admin.token()))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ARTICLE_DELETE_REQUIRES_DRAFT"));
    mockMvc.perform(get("/api/articles/{id}", publishedId)).andExpect(status().isOk());
    assertDraftUnchanged();

    jdbcTemplate.update("UPDATE articles SET content_blocks = ? WHERE id = ?", "[null]", draftId);
    mockMvc.perform(delete("/api/admin/articles/{id}", draftId)
            .session(admin.session()).header(admin.headerName(), admin.token()))
        .andExpect(status().isNoContent());
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM articles WHERE id = ?", Integer.class, draftId)).isZero();
  }

  @Test
  void editingRequiresAdministratorCsrfAndAnExistingDraft() throws Exception {
    String body = """
        {"title":"权限测试编辑","category":"%s","contentBlocks":[]}
        """.formatted(category);
    CsrfSession user = logIn(userLogin);
    mockMvc.perform(put("/api/admin/articles/{id}", draftId).session(user.session())
            .header(user.headerName(), user.token()).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    CsrfSession admin = logIn(adminLogin);
    mockMvc.perform(put("/api/admin/articles/{id}", draftId).session(admin.session())
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_FAILED"));
    mockMvc.perform(put("/api/admin/articles/{id}", publishedId).session(admin.session())
            .header(admin.headerName(), admin.token()).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ARTICLE_EDIT_REQUIRES_DRAFT"));
    mockMvc.perform(put("/api/admin/articles/{id}", "missing_" + draftId).session(admin.session())
            .header(admin.headerName(), admin.token()).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isNotFound());
    mockMvc.perform(get("/api/articles/{id}", publishedId))
        .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("发布测试文章"));
    assertThat(storedTime(publishedId)).isEqualTo(OLD_TIME);
  }

  @Test
  void adminDetailRequiresAdministratorAndReportsTheCurrentPublicationState() throws Exception {
    mockMvc.perform(get("/api/admin/articles/{id}", draftId))
        .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    CsrfSession user = logIn(userLogin);
    mockMvc.perform(get("/api/admin/articles/{id}", draftId).session(user.session()))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    CsrfSession admin = logIn(adminLogin);
    mockMvc.perform(get("/api/admin/articles/{id}", publishedId).session(admin.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("published"))
        .andExpect(jsonPath("$.article.publishedAt").value("2020-01-01T09:00:00"));
    mockMvc.perform(get("/api/admin/articles/{id}", "missing_" + draftId).session(admin.session()))
        .andExpect(status().isNotFound());
  }

  @Test
  void concurrentPublicationCallsBothReturnTheSameCommittedState() throws Exception {
    // HTTP 身份与 CSRF 由其他方法覆盖；此处用两个线程、两笔业务事务验证并发状态。
    var workers = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      var first = workers.submit(() -> { start.await(); return articleService.publish(draftId); });
      var second = workers.submit(() -> { start.await(); return articleService.publish(draftId); });
      start.countDown();
      ArticlePublicationResponse firstResult = first.get(10, TimeUnit.SECONDS);
      ArticlePublicationResponse secondResult = second.get(10, TimeUnit.SECONDS);
      assertThat(firstResult.getStatus()).isEqualTo("published");
      assertThat(secondResult.getStatus()).isEqualTo("published");
      assertThat(secondResult.getPublishedAt()).isEqualTo(firstResult.getPublishedAt());
      assertThat(storedTime(draftId)).isEqualTo(firstResult.getPublishedAt());
    } finally {
      workers.shutdownNow();
    }
  }
}
