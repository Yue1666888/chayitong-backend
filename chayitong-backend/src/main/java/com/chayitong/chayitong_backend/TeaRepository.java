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

  public void insert(TeaResponse tea) {
    // JdbcTemplate.update 可以执行 INSERT、UPDATE、DELETE，并绑定参数。
    jdbcTemplate.update(
        "INSERT INTO teas (id, name, type) VALUES (?, ?, ?)",
        tea.getId(),
        tea.getName(),
        tea.getType());
  }

  public void update(String id, String name, String type) {
    jdbcTemplate.update(
        "UPDATE teas SET name = ?, type = ? WHERE id = ?",
        name,
        type,
        id);
  }

  public int deleteById(String id) {
    // 返回删除的行数；编号是主键，因此结果是 0 或 1。
    return jdbcTemplate.update("DELETE FROM teas WHERE id = ?", id);
  }
}
