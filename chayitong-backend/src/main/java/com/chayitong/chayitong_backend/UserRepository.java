package com.chayitong.chayitong_backend;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
  private final JdbcTemplate jdbcTemplate;

  public UserRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UserResponse findById(String id) {
    List<UserResponse> users = jdbcTemplate.query(
        "SELECT id, display_name FROM users WHERE id = ?",
        (resultSet, rowNumber) -> new UserResponse(
            resultSet.getString("id"), resultSet.getString("display_name")),
        id);

    if (users.isEmpty()) {
      return null;
    }
    return users.get(0);
  }
}
