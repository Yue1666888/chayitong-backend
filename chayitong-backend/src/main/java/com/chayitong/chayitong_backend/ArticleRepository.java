package com.chayitong.chayitong_backend;

import com.chayitong.chayitong_backend.content.ContentBlockReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ArticleRepository {
  // COUNT 与列表复用同一个 WHERE，避免总数与当前页使用不同的条件。
  private static final String PUBLISHED_FILTER = """
      WHERE status = 'published'
        AND (:category IS NULL OR category = :category)
        AND (:keywordPattern IS NULL OR (
          title LIKE :keywordPattern ESCAPE '!'
          OR summary LIKE :keywordPattern ESCAPE '!'
        ))
      """;

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final ContentBlockReader contentBlockReader;

  public ArticleRepository(NamedParameterJdbcTemplate jdbcTemplate, ContentBlockReader contentBlockReader) {
    this.jdbcTemplate = jdbcTemplate;
    this.contentBlockReader = contentBlockReader;
  }

  public long countPublished(String category, String keyword) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM articles " + PUBLISHED_FILTER, filters(category, keyword), Long.class);
  }

  public List<ArticleSummaryResponse> findPublished(String category, String keyword, int limit, long offset) {
    // 不读取正文 JSON；无论调用方传什么 status，公开列表只查询已发布文章。
    return jdbcTemplate.query(
        """
        SELECT id, title, summary, category, cover, published_at
        FROM articles
        """ + PUBLISHED_FILTER + """
        ORDER BY published_at DESC, id ASC
        LIMIT :limit OFFSET :offset
        """,
        filters(category, keyword).addValue("limit", limit).addValue("offset", offset),
        (resultSet, rowNumber) -> new ArticleSummaryResponse(
            resultSet.getString("id"), resultSet.getString("title"),
            resultSet.getString("summary"), resultSet.getString("category"),
            resultSet.getString("cover"), resultSet.getObject("published_at", LocalDateTime.class)));
  }

  private MapSqlParameterSource filters(String category, String keyword) {
    return new MapSqlParameterSource()
        .addValue("category", category)
        .addValue("keywordPattern", SqlLikePattern.contains(keyword));
  }

  public ArticleDetailResponse findPublishedById(String id) {
    List<ArticleDetailResponse> articles = jdbcTemplate.query(
        """
        SELECT id, title, summary, category, cover, published_at, content_blocks
        FROM articles
        WHERE id = :id AND status = 'published'
        """,
        Map.of("id", id),
        this::mapDetail);
    return articles.isEmpty() ? null : articles.get(0);
  }

  public ArticleDetailResponse findForPublication(String id) {
    // 仅在发布业务的事务内调用：锁住目标记录，直到校验和状态更新一起完成。
    List<ArticleDetailResponse> articles = jdbcTemplate.query(
        """
        SELECT id, title, summary, category, cover, published_at, content_blocks
        FROM articles
        WHERE id = :id
        FOR UPDATE
        """,
        Map.of("id", id), this::mapDetail);
    return articles.isEmpty() ? null : articles.get(0);
  }

  public AdminArticleDetailResponse findAdminDetail(String id) {
    List<AdminArticleDetailResponse> articles = jdbcTemplate.query(
        """
        SELECT id, title, summary, category, cover, published_at, content_blocks, status
        FROM articles
        WHERE id = :id
        """,
        Map.of("id", id),
        (resultSet, rowNumber) -> new AdminArticleDetailResponse(
            resultSet.getString("status"), mapDetail(resultSet, rowNumber)));
    return articles.isEmpty() ? null : articles.get(0);
  }

  public void publishDraft(String id, LocalDateTime publishedAt) {
    // 只有草稿会更新；重复发布已发布文章时，不重写发布时间。
    jdbcTemplate.update(
        """
        UPDATE articles
        SET status = 'published', published_at = :publishedAt
        WHERE id = :id AND status = 'draft'
        """,
        new MapSqlParameterSource().addValue("id", id).addValue("publishedAt", publishedAt));
  }

  public ArticlePublicationResponse findPublicationState(String id) {
    return jdbcTemplate.queryForObject(
        "SELECT id, status, published_at FROM articles WHERE id = :id",
        Map.of("id", id),
        (resultSet, rowNumber) -> new ArticlePublicationResponse(
            resultSet.getString("id"), resultSet.getString("status"),
            resultSet.getObject("published_at", LocalDateTime.class)));
  }

  public int withdrawPublication(String id) {
    return jdbcTemplate.update(
        """
        UPDATE articles
        SET status = 'draft', published_at = NULL
        WHERE id = :id AND (status = 'published' OR published_at IS NOT NULL)
        """,
        Map.of("id", id));
  }

  public boolean existsById(String id) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM articles WHERE id = :id", Map.of("id", id), Long.class) > 0;
  }

  public void insertDraft(String id, String title, String category, String summary, String cover,
      String contentJson) {
    jdbcTemplate.update(
        """
        INSERT INTO articles (id, title, category, summary, cover, content_blocks)
        VALUES (:id, :title, :category, :summary, :cover, :contentJson)
        """,
        draftParameters(id, title, category, summary, cover, contentJson));
  }

  public String findStatusForUpdate(String id) {
    // 编辑和删除共用状态检查，正文可由后续业务按需读取。
    return jdbcTemplate.queryForList(
        "SELECT status FROM articles WHERE id = :id FOR UPDATE", Map.of("id", id), String.class)
        .stream().findFirst().orElse(null);
  }

  public void deleteDraft(String id) {
    jdbcTemplate.update("DELETE FROM articles WHERE id = :id AND status = 'draft'", Map.of("id", id));
  }

  public void updateDraft(String id, String title, String category, String summary, String cover,
      String contentJson) {
    jdbcTemplate.update(
        """
        UPDATE articles
        SET title = :title, category = :category, summary = :summary,
            cover = :cover, content_blocks = :contentJson
        WHERE id = :id AND status = 'draft'
        """,
        draftParameters(id, title, category, summary, cover, contentJson));
  }

  private MapSqlParameterSource draftParameters(String id, String title, String category, String summary,
      String cover, String contentJson) {
    return new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("title", title)
            .addValue("category", category)
            .addValue("summary", summary)
            .addValue("cover", cover)
            .addValue("contentJson", contentJson);
  }

  private ArticleDetailResponse mapDetail(ResultSet resultSet, int rowNumber) throws SQLException {
    return new ArticleDetailResponse(
        resultSet.getString("id"), resultSet.getString("title"),
        resultSet.getString("summary"), resultSet.getString("category"),
        resultSet.getString("cover"), resultSet.getObject("published_at", LocalDateTime.class),
        contentBlockReader.read(resultSet.getString("content_blocks")));
  }
}
