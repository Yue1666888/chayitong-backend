package com.chayitong.chayitong_backend;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserCredentialRepository {
  private final JdbcTemplate jdbcTemplate;

  public UserCredentialRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void insert(String userId, String loginName, String passwordHash) {
    jdbcTemplate.update(
        "INSERT INTO user_credentials (user_id, login_name, password_hash) VALUES (?, ?, ?)",
        userId, loginName, passwordHash);
  }

  public LoginUser findByLoginName(String loginName) {
    List<LoginUser> users = jdbcTemplate.query(
        """
        SELECT u.id, u.display_name, u.role, c.login_name, c.password_hash
        FROM user_credentials AS c
        JOIN users AS u ON c.user_id = u.id
        WHERE c.login_name = ?
        """,
        (resultSet, rowNumber) -> new LoginUser(resultSet.getString("id"),
            resultSet.getString("display_name"), resultSet.getString("login_name"),
            resultSet.getString("password_hash"), resultSet.getString("role")),
        loginName);
    return users.isEmpty() ? null : users.get(0);
  }
}
