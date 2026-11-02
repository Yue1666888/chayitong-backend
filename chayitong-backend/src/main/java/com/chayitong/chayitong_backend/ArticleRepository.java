package com.chayitong.chayitong_backend;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class ArticleRepository {
  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public ArticleRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public long countPublished() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM articles WHERE status = 'published'", Long.class);
  }

  public List<ArticleSummaryResponse> findPublished(int limit, long offset) {
    // 不读取正文 JSON；无论调用方传什么 status，公开列表只查询已发布文章。
    return jdbcTemplate.query(
        """
        SELECT id, title, summary, category, cover, published_at
        FROM articles
        WHERE status = 'published'
        ORDER BY published_at DESC, id ASC
        LIMIT ? OFFSET ?
        """,
        (resultSet, rowNumber) -> new ArticleSummaryResponse(
            resultSet.getString("id"), resultSet.getString("title"),
            resultSet.getString("summary"), resultSet.getString("category"),
            resultSet.getString("cover"), resultSet.getObject("published_at", LocalDateTime.class)),
        limit, offset);
  }

  public ArticleDetailResponse findPublishedById(String id) {
    List<ArticleDetailResponse> articles = jdbcTemplate.query(
        """
        SELECT id, title, summary, category, cover, published_at, content_blocks
        FROM articles
        WHERE id = ? AND status = 'published'
        """,
        (resultSet, rowNumber) -> new ArticleDetailResponse(
            resultSet.getString("id"), resultSet.getString("title"),
            resultSet.getString("summary"), resultSet.getString("category"),
            resultSet.getString("cover"), resultSet.getObject("published_at", LocalDateTime.class),
            // getString 取得 JSON 文本，readTree 将它解析成可直接序列化的 JSON 结构。
            objectMapper.readTree(resultSet.getString("content_blocks"))),
        id);
    return articles.isEmpty() ? null : articles.get(0);
  }
}
