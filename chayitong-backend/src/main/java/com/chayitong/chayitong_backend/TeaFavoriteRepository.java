package com.chayitong.chayitong_backend;

import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TeaFavoriteRepository {
  private final JdbcTemplate jdbcTemplate;

  public TeaFavoriteRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public long countByUserId(String userId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM tea_favorites WHERE user_id = ?", Long.class, userId);
  }

  public List<TeaResponse> findByUserId(String userId, int limit, long offset) {
    // 用户资料已单独读取，这里通过 JOIN 一次查出收藏的茶叶资料。
    return jdbcTemplate.query(
        """
        SELECT t.id, t.name, t.type
        FROM tea_favorites AS f
        JOIN teas AS t ON f.tea_id = t.id
        WHERE f.user_id = ?
        ORDER BY t.id
        LIMIT ? OFFSET ?
        """,
        (resultSet, rowNumber) -> new TeaResponse(
            resultSet.getString("id"), resultSet.getString("name"), resultSet.getString("type")),
        userId, limit, offset);
  }

  public boolean add(String userId, String teaId) {
    try {
      jdbcTemplate.update(
          "INSERT INTO tea_favorites (user_id, tea_id) VALUES (?, ?)", userId, teaId);
      return true;
    } catch (DuplicateKeyException exception) {
      // 联合主键重复表示已收藏；只将这一种预期情况视为成功。
      return false;
    }
  }

  public void remove(String userId, String teaId) {
    jdbcTemplate.update(
        "DELETE FROM tea_favorites WHERE user_id = ? AND tea_id = ?", userId, teaId);
  }
}
