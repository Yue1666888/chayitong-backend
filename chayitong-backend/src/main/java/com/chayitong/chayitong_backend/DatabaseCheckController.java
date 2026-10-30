package com.chayitong.chayitong_backend;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DatabaseCheckController {

  private final JdbcTemplate jdbcTemplate;

  public DatabaseCheckController(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @GetMapping("/api/database/check")
  public String checkDatabase() {
    return jdbcTemplate.queryForObject(
        "SELECT DATABASE()",
        String.class);
  }
}
