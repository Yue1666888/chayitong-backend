package com.chayitong.chayitong_backend;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class TeaRepository {

  private final JdbcTemplate jdbcTemplate;

  // 将 SQL 查询结果中的每一行转换为一个 Java 对象。
  private final RowMapper<TeaResponse> teaRowMapper = (resultSet, rowNumber) ->
      new TeaResponse(
          resultSet.getString("id"),
          resultSet.getString("name"),
          resultSet.getString("type"));

  public TeaRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public TeaResponse findById(String id) {
    List<TeaResponse> teas = jdbcTemplate.query(
        "SELECT id, name, type FROM teas WHERE id = ?",
        teaRowMapper,
        id);

    if (teas.isEmpty()) {
      return null;
    }
    return teas.get(0);
  }
}
