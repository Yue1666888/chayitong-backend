package com.chayitong.chayitong_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

// 完整过滤器链，真实查库和密码校验；临时凭据随事务回滚。
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfSystemProperty(named = "db.integration", matches = "true")
class SessionSecurityIntegrationTests {
  private static final String TEST_PASSWORD = "Auth-Test-2026!";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper objectMapper;

  private String userId;
  private String otherUserId;
  private String loginName;
  private String teaId;
  private String otherTeaId;

  private record CsrfSession(MockHttpSession session, String headerName, String token) { }

  @BeforeEach
  void createTemporaryLoginAccount() {
    String suffix = UUID.randomUUID().toString().replace("-", "");
    userId = "auth_" + suffix;
    otherUserId = "other_" + suffix;
    loginName = "login_" + suffix;
    teaId = "au_" + suffix.substring(0, 12) + "1";
    otherTeaId = "au_" + suffix.substring(0, 12) + "2";
    jdbcTemplate.update("INSERT INTO users (id, display_name) VALUES (?, ?)", userId, "登录测试用户");
    jdbcTemplate.update("INSERT INTO users (id, display_name) VALUES (?, ?)", otherUserId, "其他用户");
    jdbcTemplate.update("INSERT INTO user_credentials (user_id, login_name, password_hash) VALUES (?, ?, ?)",
        userId, loginName, passwordEncoder.encode(TEST_PASSWORD));
    for (String id : new String[] {teaId, otherTeaId}) {
      jdbcTemplate.update("INSERT INTO teas (id, name, type) VALUES (?, ?, ?)", id, "登录测试茶", "绿茶");
    }
    jdbcTemplate.update("INSERT INTO tea_favorites (user_id, tea_id) VALUES (?, ?)", userId, teaId);
    jdbcTemplate.update("INSERT INTO tea_favorites (user_id, tea_id) VALUES (?, ?)", otherUserId, teaId);
  }

  @AfterTransaction
  void temporaryCredentialsWereRolledBack() {
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM users WHERE id IN (?, ?)", Integer.class, userId, otherUserId)).isZero();
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM user_credentials WHERE user_id = ?", Integer.class, userId)).isZero();
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM teas WHERE id IN (?, ?)", Integer.class, teaId, otherTeaId)).isZero();
  }

  private CsrfSession fetchCsrf(MockHttpSession session) throws Exception {
    var request = get("/api/auth/csrf");
    if (session != null) {
      request.session(session);
    }
    MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
    Map<?, ?> data = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    return new CsrfSession((MockHttpSession) result.getRequest().getSession(false),
        (String) data.get("headerName"), (String) data.get("token"));
  }

  private MockHttpSession logIn(CsrfSession csrf) throws Exception {
    MvcResult result = mockMvc.perform(post("/api/auth/login").session(csrf.session())
            .with(request -> {
              // 模拟客户端携带有效的会话 Cookie，供会话固定防护判断。
              request.setRequestedSessionId(csrf.session().getId());
              request.setRequestedSessionIdValid(true);
              return request;
            })
            .header(csrf.headerName(), csrf.token())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("username", loginName).param("password", TEST_PASSWORD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(userId))
        .andExpect(jsonPath("$.displayName").value("登录测试用户"))
        .andExpect(jsonPath("$.password").doesNotExist())
        .andExpect(jsonPath("$.passwordHash").doesNotExist())
        .andReturn();
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private List<MockHttpServletRequestBuilder> teaWriteRequests() {
    String newTea = """
        {"id":"%s","name":"权限测试新茶","type":"绿茶"}
        """.formatted(teaId + "_new");
    return List.of(
        post("/api/teas").contentType(MediaType.APPLICATION_JSON).content(newTea),
        post("/api/teas/batch").contentType(MediaType.APPLICATION_JSON)
            .content("{\"items\":[" + newTea + "]}"),
        post("/api/teas/preview").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"预览茶\",\"type\":\"绿茶\"}"),
        put("/api/teas/{id}", teaId).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"不应被修改\",\"type\":\"红茶\"}"),
        delete("/api/teas/{id}", teaId));
  }

  @Test
  void anonymousPrivateRequestsReturnJson401ButTeaReadsRemainPublic() throws Exception {
    mockMvc.perform(get("/api/users/{id}/favorites", userId))
        .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    mockMvc.perform(get("/api/auth/me"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/teas"))
        .andExpect(status().isOk());
  }

  @Test
  void loginPersistsIdentityRotatesSessionIdAndErasesHash() throws Exception {
    CsrfSession csrf = fetchCsrf(null);
    String previousSessionId = csrf.session().getId();
    MockHttpSession session = logIn(csrf);
    assertThat(session.getId()).isNotEqualTo(previousSessionId);
    SecurityContext context = (SecurityContext) session.getAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
    LoginUser principal = (LoginUser) context.getAuthentication().getPrincipal();
    assertThat(principal.getPassword()).isNull();
    assertThat(principal.getRole()).isEqualTo("USER");
    assertThat(principal.getAuthorities()).extracting(authority -> authority.getAuthority())
        .containsExactly("ROLE_USER");
    mockMvc.perform(get("/api/auth/me").session(session))
        .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(userId));
  }

  @Test
  void wrongPasswordAndUnknownLoginNameReturnTheSameGenericError() throws Exception {
    for (String username : new String[] {loginName, "missing_" + loginName}) {
      CsrfSession csrf = fetchCsrf(null);
      mockMvc.perform(post("/api/auth/login").session(csrf.session())
              .header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .param("username", username).param("password", "Wrong-Test-Password"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
          .andExpect(jsonPath("$.message").value("登录名或密码错误"));
      mockMvc.perform(get("/api/auth/me").session(csrf.session()))
          .andExpect(status().isUnauthorized());
    }
  }

  @Test
  void authenticatedUserCanReadOnlyTheirOwnFavorites() throws Exception {
    MockHttpSession session = logIn(fetchCsrf(null));
    mockMvc.perform(get("/api/users/{id}/favorites", userId).session(session))
        .andExpect(status().isOk()).andExpect(jsonPath("$.user.id").value(userId));
    mockMvc.perform(get("/api/users/{id}/favorites", otherUserId).session(session))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void myFavoritesRequiresLogin() throws Exception {
    mockMvc.perform(get("/api/me/favorites"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  void myFavoritesUsesTheLoginIdentityEvenIfAnotherUserIdIsSubmitted() throws Exception {
    MockHttpSession session = logIn(fetchCsrf(null));
    jdbcTemplate.update("INSERT INTO tea_favorites (user_id, tea_id) VALUES (?, ?)", otherUserId, otherTeaId);
    mockMvc.perform(get("/api/me/favorites").session(session)
            .param("userId", otherUserId).param("page", "1").param("pageSize", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.id").value(userId))
        .andExpect(jsonPath("$.favorites.page").value(1))
        .andExpect(jsonPath("$.favorites.pageSize").value(1))
        .andExpect(jsonPath("$.favorites.total").value(1))
        .andExpect(jsonPath("$.favorites.items.length()").value(1))
        .andExpect(jsonPath("$.favorites.items[0].id").value(teaId));
    mockMvc.perform(get("/api/me/favorites").session(session)
            .param("page", "2").param("pageSize", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.favorites.total").value(1))
        .andExpect(jsonPath("$.favorites.items.length()").value(0));
  }

  @Test
  void myFavoritesWithoutAnyFavoritesReturnsAnEmptyPage() throws Exception {
    MockHttpSession session = logIn(fetchCsrf(null));
    jdbcTemplate.update("DELETE FROM tea_favorites WHERE user_id = ?", userId);
    mockMvc.perform(get("/api/me/favorites").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.id").value(userId))
        .andExpect(jsonPath("$.favorites.total").value(0))
        .andExpect(jsonPath("$.favorites.items.length()").value(0));
  }

  @Test
  void myFavoritesValidatesPagination() throws Exception {
    MockHttpSession session = logIn(fetchCsrf(null));
    for (String[] parameter : new String[][] {{"page", "0"}, {"pageSize", "101"}, {"page", "abc"}}) {
      mockMvc.perform(get("/api/me/favorites").session(session).param(parameter[0], parameter[1]))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.fieldErrors." + parameter[0]).exists());
    }
  }

  @Test
  void myFavoriteWritesAreIdempotentAndOnlyAffectTheLoginUser() throws Exception {
    MockHttpSession session = logIn(fetchCsrf(null));
    CsrfSession csrf = fetchCsrf(session);
    mockMvc.perform(put("/api/me/favorites/{tea}", otherTeaId).session(session)
            .header(csrf.headerName(), csrf.token()).param("userId", otherUserId))
        .andExpect(status().isCreated());
    mockMvc.perform(put("/api/me/favorites/{tea}", otherTeaId).session(session)
            .header(csrf.headerName(), csrf.token()))
        .andExpect(status().isNoContent());
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM tea_favorites WHERE user_id = ? AND tea_id = ?",
        Integer.class, userId, otherTeaId)).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM tea_favorites WHERE user_id = ? AND tea_id = ?",
        Integer.class, otherUserId, otherTeaId)).isZero();

    // 两位用户都收藏了 teaId；取消收藏只能删除当前用户的记录。
    for (int attempt = 0; attempt < 2; attempt++) {
      mockMvc.perform(delete("/api/me/favorites/{tea}", teaId).session(session)
              .header(csrf.headerName(), csrf.token()).param("userId", otherUserId))
          .andExpect(status().isNoContent());
    }
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM tea_favorites WHERE user_id = ? AND tea_id = ?",
        Integer.class, userId, teaId)).isZero();
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM tea_favorites WHERE user_id = ? AND tea_id = ?",
        Integer.class, otherUserId, teaId)).isEqualTo(1);
  }

  @Test
  void myFavoriteWritesRejectMissingOrPreLoginCsrfTokens() throws Exception {
    CsrfSession oldCsrf = fetchCsrf(null);
    MockHttpSession session = logIn(oldCsrf);
    for (var request : List.of(put("/api/me/favorites/{tea}", teaId),
        delete("/api/me/favorites/{tea}", teaId))) {
      mockMvc.perform(request.session(session))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("CSRF_FAILED"));
      mockMvc.perform(request.session(session).header(oldCsrf.headerName(), oldCsrf.token()))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("CSRF_FAILED"));
    }
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM tea_favorites WHERE user_id = ? AND tea_id = ?",
        Integer.class, userId, teaId)).isEqualTo(1);
  }

  @Test
  void myFavoriteWritesRequireLoginEvenWithValidCsrf() throws Exception {
    CsrfSession csrf = fetchCsrf(null);
    for (var request : List.of(put("/api/me/favorites/{tea}", teaId),
        delete("/api/me/favorites/{tea}", teaId))) {
      mockMvc.perform(request.session(csrf.session()).header(csrf.headerName(), csrf.token()))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM tea_favorites WHERE user_id = ? AND tea_id = ?",
        Integer.class, userId, teaId)).isEqualTo(1);
  }

  @Test
  void myFavoriteCannotBeCreatedForANonexistentTea() throws Exception {
    MockHttpSession session = logIn(fetchCsrf(null));
    CsrfSession csrf = fetchCsrf(session);
    mockMvc.perform(put("/api/me/favorites/{tea}", "missing_" + teaId).session(session)
            .header(csrf.headerName(), csrf.token()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("HTTP_404"));
  }

  @Test
  void writingNeedsAFreshCsrfTokenAndThenSucceeds() throws Exception {
    MockHttpSession session = logIn(fetchCsrf(null));
    mockMvc.perform(put("/api/users/{id}/favorites/{tea}", userId, otherTeaId).session(session))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_FAILED"));
    CsrfSession csrf = fetchCsrf(session);
    mockMvc.perform(put("/api/users/{id}/favorites/{tea}", userId, otherTeaId).session(session)
            .header(csrf.headerName(), csrf.token()))
        .andExpect(status().isCreated());
    mockMvc.perform(delete("/api/users/{id}/favorites/{tea}", userId, otherTeaId).session(session)
            .header(csrf.headerName(), csrf.token()))
        .andExpect(status().isNoContent());
  }

  @Test
  void loginInvalidatesThePreLoginCsrfToken() throws Exception {
    CsrfSession oldCsrf = fetchCsrf(null);
    MockHttpSession session = logIn(oldCsrf);
    mockMvc.perform(put("/api/users/{id}/favorites/{tea}", userId, otherTeaId).session(session)
            .header(oldCsrf.headerName(), oldCsrf.token()))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_FAILED"));
  }

  @Test
  void validCsrfDoesNotAllowChangingAnotherUsersFavorites() throws Exception {
    MockHttpSession session = logIn(fetchCsrf(null));
    CsrfSession csrf = fetchCsrf(session);
    mockMvc.perform(delete("/api/users/{id}/favorites/{tea}", otherUserId, teaId).session(session)
            .header(csrf.headerName(), csrf.token()))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM tea_favorites WHERE user_id = ? AND tea_id = ?",
        Integer.class, otherUserId, teaId)).isEqualTo(1);
  }

  @Test
  void logoutInvalidatesTheSession() throws Exception {
    MockHttpSession session = logIn(fetchCsrf(null));
    CsrfSession csrf = fetchCsrf(session);
    mockMvc.perform(post("/api/auth/logout").session(session).header(csrf.headerName(), csrf.token()))
        .andExpect(status().isNoContent());
    assertThat(session.isInvalid()).isTrue();
    mockMvc.perform(get("/api/auth/me"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void loginWithoutCsrfIsRejected() throws Exception {
    mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("username", loginName).param("password", TEST_PASSWORD))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_FAILED"));
  }

  @Test
  void teaWritesCheckCsrfAndRequireLogin() throws Exception {
    mockMvc.perform(post("/api/teas").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_FAILED"));
    CsrfSession csrf = fetchCsrf(null);
    for (var request : teaWriteRequests()) {
      mockMvc.perform(request.session(csrf.session()).header(csrf.headerName(), csrf.token()))
          .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
  }

  @Test
  void normalUserCannotMaintainTeasEvenWhenAnAdminRoleIsSubmitted() throws Exception {
    MockHttpSession session = logIn(fetchCsrf(null));
    CsrfSession csrf = fetchCsrf(session);
    for (var request : teaWriteRequests()) {
      mockMvc.perform(request.session(session).header(csrf.headerName(), csrf.token())
              .header("X-Role", "ADMIN").param("role", "ADMIN"))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("FORBIDDEN"))
          .andExpect(jsonPath("$.message").value("当前账号无权执行此操作"));
    }
    assertThat(jdbcTemplate.queryForObject(
        "SELECT name FROM teas WHERE id = ?", String.class, teaId)).isEqualTo("登录测试茶");
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM teas WHERE id = ?", Integer.class, teaId + "_new")).isZero();
    mockMvc.perform(get("/api/teas/{id}", teaId).session(session)).andExpect(status().isOk());
  }

  @Test
  void administratorCanCreateUpdateBatchCreateAndDeleteTeas() throws Exception {
    // 仅提升事务内的临时测试用户，测试结束后回滚。
    jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", userId);
    MockHttpSession session = logIn(fetchCsrf(null));
    SecurityContext context = (SecurityContext) session.getAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
    LoginUser principal = (LoginUser) context.getAuthentication().getPrincipal();
    assertThat(principal.getRole()).isEqualTo("ADMIN");
    assertThat(principal.getAuthorities()).extracting(authority -> authority.getAuthority())
        .containsExactly("ROLE_ADMIN");
    CsrfSession csrf = fetchCsrf(session);
    String newId = teaId + "_new";
    String batchId = teaId + "_b";
    mockMvc.perform(post("/api/teas").session(session).header(csrf.headerName(), csrf.token())
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"id":"%s","name":"管理员新增茶","type":"绿茶"}
                """.formatted(newId)))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(newId));
    mockMvc.perform(put("/api/teas/{id}", newId).session(session).header(csrf.headerName(), csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"管理员修改茶\",\"type\":\"红茶\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("管理员修改茶"));
    mockMvc.perform(post("/api/teas/batch").session(session).header(csrf.headerName(), csrf.token())
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"items":[{"id":"%s","name":"管理员批量茶","type":"绿茶"}]}
                """.formatted(batchId)))
        .andExpect(status().isCreated()).andExpect(jsonPath("$[0].id").value(batchId));
    mockMvc.perform(post("/api/teas/preview").session(session).header(csrf.headerName(), csrf.token())
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"预览茶\",\"type\":\"绿茶\"}"))
        .andExpect(status().isOk());
    for (String id : new String[] {newId, batchId}) {
      mockMvc.perform(delete("/api/teas/{id}", id).session(session).header(csrf.headerName(), csrf.token()))
          .andExpect(status().isNoContent());
      assertThat(jdbcTemplate.queryForObject(
          "SELECT COUNT(*) FROM teas WHERE id = ?", Integer.class, id)).isZero();
    }
  }

  @Test
  void administratorStillNeedsValidCsrfForEveryTeaWrite() throws Exception {
    jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", userId);
    MockHttpSession session = logIn(fetchCsrf(null));
    for (var request : teaWriteRequests()) {
      mockMvc.perform(request.session(session))
          .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_FAILED"));
    }
    assertThat(jdbcTemplate.queryForObject(
        "SELECT name FROM teas WHERE id = ?", String.class, teaId)).isEqualTo("登录测试茶");
  }

  @Test
  void administratorCanManageTheirOwnFavoritesButNotOtherUsersFavorites() throws Exception {
    jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", userId);
    MockHttpSession session = logIn(fetchCsrf(null));
    CsrfSession csrf = fetchCsrf(session);
    mockMvc.perform(get("/api/me/favorites").session(session))
        .andExpect(status().isOk()).andExpect(jsonPath("$.user.id").value(userId));
    mockMvc.perform(put("/api/me/favorites/{tea}", otherTeaId).session(session)
            .header(csrf.headerName(), csrf.token())).andExpect(status().isCreated());
    mockMvc.perform(delete("/api/me/favorites/{tea}", otherTeaId).session(session)
            .header(csrf.headerName(), csrf.token())).andExpect(status().isNoContent());
    for (var request : List.of(get("/api/users/{id}/favorites", otherUserId),
        put("/api/users/{id}/favorites/{tea}", otherUserId, otherTeaId),
        delete("/api/users/{id}/favorites/{tea}", otherUserId, teaId))) {
      mockMvc.perform(request.session(session).header(csrf.headerName(), csrf.token()))
          .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM tea_favorites WHERE user_id = ? AND tea_id = ?",
        Integer.class, otherUserId, teaId)).isEqualTo(1);
  }

  @Test
  void roleChangesAreLoadedOnTheNextLogin() throws Exception {
    MockHttpSession session = logIn(fetchCsrf(null));
    CsrfSession csrf = fetchCsrf(session);
    jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", userId);
    mockMvc.perform(post("/api/teas/preview").session(session).header(csrf.headerName(), csrf.token())
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"预览茶\",\"type\":\"绿茶\"}"))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    mockMvc.perform(post("/api/auth/logout").session(session).header(csrf.headerName(), csrf.token()))
        .andExpect(status().isNoContent());
    CsrfSession fresh = fetchCsrf(logIn(fetchCsrf(null)));
    mockMvc.perform(post("/api/teas/preview").session(fresh.session()).header(fresh.headerName(), fresh.token())
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"预览茶\",\"type\":\"绿茶\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void anonymousTeaDetailsAndHeadRequestsRemainPublic() throws Exception {
    mockMvc.perform(get("/api/teas/{id}", teaId)).andExpect(status().isOk());
    mockMvc.perform(head("/api/teas/{id}", teaId)).andExpect(status().isOk());
  }
}
