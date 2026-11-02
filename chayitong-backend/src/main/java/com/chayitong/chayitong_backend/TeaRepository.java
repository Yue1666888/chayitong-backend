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

  public long countAll() {
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM teas", Long.class);
  }

  public long countByType(String type) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM teas WHERE type = ?", Long.class, type);
  }

  public List<TeaResponse> findAll(int limit, long offset) {
    return jdbcTemplate.query(
        "SELECT id, name, type FROM teas ORDER BY id LIMIT ? OFFSET ?",
        teaRowMapper,
        limit,
        offset);
  }

  public List<TeaResponse> findByType(String type, int limit, long offset) {
    // ? 绑定参数值，客户端输入不会被拼接成 SQL 语句。
    return jdbcTemplate.query(
        "SELECT id, name, type FROM teas WHERE type = ? ORDER BY id LIMIT ? OFFSET ?",
        teaRowMapper,
        type,
        limit,
        offset);
  }

  public long countByKeyword(String keyword, String type) {
    return jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*) FROM teas
        WHERE name LIKE ? ESCAPE '!'
          AND (? IS NULL OR type = ?)
        """,
        Long.class, containsPattern(keyword), type, type);
  }

  public List<TeaResponse> findByKeyword(String keyword, String type, int limit, long offset) {
    // 总数查询与列表查询使用相同的过滤条件，分页只影响列表。
    return jdbcTemplate.query(
        """
        SELECT id, name, type FROM teas
        WHERE name LIKE ? ESCAPE '!'
          AND (? IS NULL OR type = ?)
        ORDER BY id LIMIT ? OFFSET ?
        """,
        teaRowMapper, containsPattern(keyword), type, type, limit, offset);
  }

  private String containsPattern(String keyword) {
    // ? 防止 SQL 注入；另将 LIKE 的特殊字符转成普通字符，按用户输入的原文搜索。
    String escaped = keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    return "%" + escaped + "%";
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
