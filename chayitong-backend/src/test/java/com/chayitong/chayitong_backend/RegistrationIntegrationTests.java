package com.chayitong.chayitong_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

// 无测试外层事务，真实验证注册提交，以及凭据写入失败时用户资料的回滚。
// 使用唯一的测试昵称定位临时数据；每次测试后删除，外键同时清理凭据。
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "db.integration", matches = "true")
class RegistrationIntegrationTests {
  private static final String TEST_PASSWORD = "Register-Test-2026!";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper objectMapper;

  private String loginName;
  private String displayName;

  private record CsrfSession(MockHttpSession session, String headerName, String token) { }

  @BeforeEach
  void prepareUniqueAccountNames() {
    String suffix = UUID.randomUUID().toString().replace("-", "");
    loginName = "reg_" + suffix.substring(0, 24);
    displayName = "registration_test_" + suffix.substring(0, 24);
  }

  @AfterEach
  void removeTemporaryAccounts() {
    jdbcTemplate.update("DELETE FROM users WHERE display_name = ?", displayName);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM user_credentials WHERE login_name = ?", Integer.class, loginName)).isZero();
  }

  private CsrfSession fetchCsrf() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
        .andExpect(status().isOk()).andReturn();
    var data = objectMapper.readTree(result.getResponse().getContentAsString());
    return new CsrfSession((MockHttpSession) result.getRequest().getSession(false),
        data.get("headerName").asString(), data.get("token").asString());
  }

  private Map<String, String> registrationBody() {
    return new HashMap<>(Map.of(
        "loginName", loginName, "password", TEST_PASSWORD, "displayName", displayName));
  }

  private MockHttpServletRequestBuilder register(CsrfSession csrf, Map<String, String> body) {
    return post("/api/auth/register").session(csrf.session())
        .header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body));
  }

  private int countTestUsers() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM users WHERE display_name = ?", Integer.class, displayName);
  }

  @Test
  void registrationCommitsBothTablesAndTheNewAccountCanLogIn() throws Exception {
    CsrfSession csrf = fetchCsrf();
    MvcResult result = mockMvc.perform(register(csrf, registrationBody()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.displayName").value(displayName))
        .andExpect(jsonPath("$.password").doesNotExist())
        .andExpect(jsonPath("$.passwordHash").doesNotExist())
        .andExpect(jsonPath("$.password_hash").doesNotExist())
        .andReturn();
    String userId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString();
    assertThat(UUID.fromString(userId).toString()).isEqualTo(userId);
    assertThat(countTestUsers()).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT role FROM users WHERE id = ?", String.class, userId)).isEqualTo("USER");
    String hash = jdbcTemplate.queryForObject(
        "SELECT password_hash FROM user_credentials WHERE user_id = ? AND login_name = ?",
        String.class, userId, loginName);
    assertThat(hash).startsWith("{bcrypt}").isNotEqualTo(TEST_PASSWORD);
    assertThat(passwordEncoder.matches(TEST_PASSWORD, hash)).isTrue();
    // 注册只创建账号，尚未写入登录身份。
    mockMvc.perform(get("/api/auth/me").session(csrf.session()))
        .andExpect(status().isUnauthorized());

    MvcResult login = mockMvc.perform(post("/api/auth/login").session(csrf.session())
            .header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("username", loginName).param("password", TEST_PASSWORD))
        .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(userId)).andReturn();
    MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
    mockMvc.perform(get("/api/me/favorites").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.id").value(userId))
        .andExpect(jsonPath("$.favorites.total").value(0));
  }

  @Test
  void registrationCannotChooseAnAdministratorRole() throws Exception {
    CsrfSession csrf = fetchCsrf();
    Map<String, String> body = registrationBody();
    body.put("role", "ADMIN");
    mockMvc.perform(register(csrf, body)).andExpect(status().isCreated());
    assertThat(jdbcTemplate.queryForObject(
        "SELECT role FROM users WHERE display_name = ?", String.class, displayName)).isEqualTo("USER");
  }

  @Test
  void duplicateLoginNameRollsBackTheUserInsertAndPreservesTheOriginalPassword() throws Exception {
    CsrfSession csrf = fetchCsrf();
    mockMvc.perform(register(csrf, registrationBody())).andExpect(status().isCreated());
    String originalHash = jdbcTemplate.queryForObject(
        "SELECT password_hash FROM user_credentials WHERE login_name = ?", String.class, loginName);
    // 数据库使用不区分大小写的排序规则，同名和大小写变体都应被唯一约束拒绝。
    for (String duplicate : new String[] {loginName, loginName.toUpperCase(Locale.ROOT)}) {
      Map<String, String> body = registrationBody();
      body.put("loginName", duplicate);
      body.put("password", "Another-Test-Password!");
      mockMvc.perform(register(csrf, body)).andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("DATA_CONFLICT"));
      // 每次请求已结束；若业务事务没有回滚，这里会出现多余的用户资料。
      assertThat(countTestUsers()).isEqualTo(1);
      assertThat(jdbcTemplate.queryForObject(
          "SELECT password_hash FROM user_credentials WHERE login_name = ?", String.class, loginName))
          .isEqualTo(originalHash);
    }
  }

  @Test
  void registrationWithoutCsrfIsRejectedBeforeWriting() throws Exception {
    mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registrationBody())))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_FAILED"));
    assertThat(countTestUsers()).isZero();
  }

  @Test
  void invalidFieldsAreRejectedBeforeWriting() throws Exception {
    CsrfSession csrf = fetchCsrf();
    for (String[] field : new String[][] {
        {"loginName", "ab"}, {"loginName", "a".repeat(33)}, {"loginName", "bad name"},
        {"password", "short"}, {"password", "p".repeat(65)}, {"password", "中文密码测试123"},
        {"displayName", "   "}, {"displayName", "名".repeat(51)}, {"password", null}}) {
      Map<String, String> body = registrationBody();
      body.put(field[0], field[1]);
      mockMvc.perform(register(csrf, body)).andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.fieldErrors." + field[0]).exists());
    }
    assertThat(countTestUsers()).isZero();
  }
}
