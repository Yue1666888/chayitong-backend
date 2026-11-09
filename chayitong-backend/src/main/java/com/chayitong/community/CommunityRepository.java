package com.chayitong.community;

import com.chayitong.community.CommunityRecords.Author;
import com.chayitong.community.CommunityRecords.Comment;
import com.chayitong.community.CommunityRecords.Like;
import com.chayitong.community.CommunityRecords.Media;
import com.chayitong.community.CommunityRecords.Post;
import com.chayitong.community.CommunityRecords.PostMedia;
import com.chayitong.community.CommunityRecords.Tea;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
class CommunityRepository {

    private final JdbcTemplate jdbcTemplate;

    CommunityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<Post> listPublicPosts(String topic, int limit, int offset) {
        if (topic == null) {
            return jdbcTemplate.query("""
                    SELECT * FROM post
                    WHERE deleted = 0 AND status = 'published'
                    ORDER BY published_at DESC, id DESC LIMIT ? OFFSET ?
                    """, postRowMapper(), limit, offset);
        }
        return jdbcTemplate.query("""
                SELECT * FROM post
                WHERE deleted = 0 AND status = 'published' AND topic = ?
                ORDER BY published_at DESC, id DESC LIMIT ? OFFSET ?
                """, postRowMapper(), topic, limit, offset);
    }

    long countPublicPosts(String topic) {
        if (topic == null) {
            return jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM post WHERE deleted = 0 AND status = 'published'", Long.class);
        }
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post WHERE deleted = 0 AND status = 'published' AND topic = ?",
                Long.class, topic);
    }

    List<Post> listMyPosts(long authorId, int limit, int offset) {
        return jdbcTemplate.query("""
                SELECT * FROM post
                WHERE author_id = ? AND deleted = 0 AND status IN ('published', 'hidden')
                ORDER BY published_at DESC, id DESC LIMIT ? OFFSET ?
                """, postRowMapper(), authorId, limit, offset);
    }

    long countMyPosts(long authorId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM post
                WHERE author_id = ? AND deleted = 0 AND status IN ('published', 'hidden')
                """, Long.class, authorId);
    }

    Optional<Post> findPost(String businessKey) {
        return queryOne("SELECT * FROM post WHERE business_key = ?", postRowMapper(), businessKey);
    }

    Optional<Post> findPostById(long id) {
        return queryOne("SELECT * FROM post WHERE id = ?", postRowMapper(), id);
    }

    Optional<Post> lockPostById(long id) {
        return queryOne("SELECT * FROM post WHERE id = ? FOR UPDATE", postRowMapper(), id);
    }

    Optional<Post> findPublicPost(String businessKey) {
        return queryOne("SELECT * FROM post WHERE business_key = ? AND deleted = 0 AND status = 'published'",
                postRowMapper(), businessKey);
    }

    Optional<Post> lockPost(String businessKey) {
        return queryOne("SELECT * FROM post WHERE business_key = ? FOR UPDATE", postRowMapper(), businessKey);
    }

    Optional<Post> findPostByRequest(long authorId, String clientRequestId) {
        return queryOne("SELECT * FROM post WHERE author_id = ? AND client_request_id = ?",
                postRowMapper(), authorId, clientRequestId);
    }

    Optional<Post> findPostByRequestForShare(long authorId, String clientRequestId) {
        return queryOne("SELECT * FROM post WHERE author_id = ? AND client_request_id = ? FOR SHARE",
                postRowMapper(), authorId, clientRequestId);
    }

    void insertPost(long id, String businessKey, long authorId, String clientRequestId, String requestHash,
            String content, String topic, String tags, Long relatedTeaId, LocalDateTime now) {
        jdbcTemplate.update("""
                INSERT INTO post (id, business_key, author_id, client_request_id, request_hash,
                    content, topic, tags, related_tea_id, status, like_count, comment_count,
                    published_at, created_at, updated_at, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'published', 0, 0, ?, ?, ?, 0)
                """, id, businessKey, authorId, clientRequestId, requestHash, content, topic, tags,
                relatedTeaId, now, now, now);
    }

    void markPostDeleted(long id, LocalDateTime now) {
        jdbcTemplate.update("UPDATE post SET deleted = 1, status = 'deleted', updated_at = ? WHERE id = ?",
                now, id);
    }

    void updatePostStatus(long id, String status, LocalDateTime now) {
        jdbcTemplate.update("UPDATE post SET status = ?, updated_at = ? WHERE id = ?", status, now, id);
    }

    void incrementLikeCount(long postId, LocalDateTime now) {
        jdbcTemplate.update("UPDATE post SET like_count = like_count + 1, updated_at = ? WHERE id = ?",
                now, postId);
    }

    void decrementLikeCount(long postId, LocalDateTime now) {
        jdbcTemplate.update("""
                UPDATE post SET like_count = GREATEST(like_count - 1, 0), updated_at = ? WHERE id = ?
                """, now, postId);
    }

    void incrementCommentCount(long postId, LocalDateTime now) {
        jdbcTemplate.update("UPDATE post SET comment_count = comment_count + 1, updated_at = ? WHERE id = ?",
                now, postId);
    }

    void decrementCommentCount(long postId, LocalDateTime now) {
        jdbcTemplate.update("""
                UPDATE post SET comment_count = GREATEST(comment_count - 1, 0), updated_at = ? WHERE id = ?
                """, now, postId);
    }

    Optional<Author> findAuthor(long userId) {
        return queryOne("""
                SELECT id, business_key, display_name, avatar_media_id, points_balance FROM users WHERE id = ?
                """, (rs, rowNum) -> new Author(rs.getLong("id"), rs.getString("business_key"),
                rs.getString("display_name"), nullableLong(rs, "avatar_media_id"),
                rs.getLong("points_balance")), userId);
    }

    Optional<Media> findMediaById(long id) {
        return queryOne("SELECT * FROM media_file WHERE id = ?", mediaRowMapper(), id);
    }

    Optional<Media> lockMedia(String mediaId) {
        return queryOne("SELECT * FROM media_file WHERE media_id = ? FOR UPDATE", mediaRowMapper(), mediaId);
    }

    void bindMedia(long mediaId, String postBusinessKey, LocalDateTime now) {
        jdbcTemplate.update("""
                UPDATE media_file SET status = 'BOUND', bound_type = 'post', bound_id = ?, updated_at = ?
                WHERE id = ?
                """, postBusinessKey, now, mediaId);
    }

    void insertPostMedia(long id, long postId, long mediaId, String role, int sortOrder, LocalDateTime now) {
        jdbcTemplate.update("""
                INSERT INTO post_media (id, post_id, media_pk, media_role, sort_order,
                    created_at, updated_at, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0)
                """, id, postId, mediaId, role, sortOrder, now, now);
    }

    List<PostMedia> findPostMedia(long postId) {
        return jdbcTemplate.query("""
                SELECT pm.media_role, pm.sort_order, m.*
                FROM post_media pm JOIN media_file m ON m.id = pm.media_pk
                WHERE pm.post_id = ? AND pm.deleted = 0
                ORDER BY pm.sort_order ASC, pm.id ASC
                """, (rs, rowNum) -> new PostMedia(rs.getString("media_role"), rs.getInt("sort_order"),
                mapMedia(rs)), postId);
    }

    Optional<Tea> findPublicTea(String businessKey) {
        return queryOne("SELECT * FROM tea WHERE business_key = ? AND deleted = 0 AND status = 'published'",
                teaRowMapper(), businessKey);
    }

    Optional<Tea> findPublicTea(long id) {
        return queryOne("SELECT * FROM tea WHERE id = ? AND deleted = 0 AND status = 'published'",
                teaRowMapper(), id);
    }

    Optional<Like> lockLike(long postId, long userId) {
        return queryOne("SELECT * FROM post_like WHERE post_id = ? AND user_id = ? FOR UPDATE",
                (rs, rowNum) -> new Like(rs.getLong("id"), rs.getLong("post_id"), rs.getLong("user_id"),
                        rs.getInt("deleted")), postId, userId);
    }

    boolean isLiked(long postId, long userId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_like WHERE post_id = ? AND user_id = ? AND deleted = 0",
                Long.class, postId, userId);
        return count != null && count > 0;
    }

    void insertLike(long id, long postId, long userId, LocalDateTime now) {
        jdbcTemplate.update("""
                INSERT INTO post_like (id, post_id, user_id, liked_at, created_at, updated_at, deleted)
                VALUES (?, ?, ?, ?, ?, ?, 0)
                """, id, postId, userId, now, now, now);
    }

    void restoreLike(long id, LocalDateTime now) {
        jdbcTemplate.update("""
                UPDATE post_like SET deleted = 0, liked_at = ?, updated_at = ? WHERE id = ?
                """, now, now, id);
    }

    void removeLike(long id, LocalDateTime now) {
        jdbcTemplate.update("UPDATE post_like SET deleted = 1, updated_at = ? WHERE id = ?", now, id);
    }

    List<Comment> listComments(long postId, int limit, int offset) {
        return jdbcTemplate.query("""
                SELECT * FROM post_comment WHERE post_id = ? AND deleted = 0
                ORDER BY created_at ASC, id ASC LIMIT ? OFFSET ?
                """, commentRowMapper(), postId, limit, offset);
    }

    List<Comment> commentPreview(long postId) {
        return jdbcTemplate.query("""
                SELECT * FROM post_comment WHERE post_id = ? AND deleted = 0
                ORDER BY created_at ASC, id ASC LIMIT 3
                """, commentRowMapper(), postId);
    }

    long countComments(long postId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_comment WHERE post_id = ? AND deleted = 0", Long.class, postId);
    }

    Optional<Comment> findCommentByRequest(long authorId, String clientRequestId) {
        return queryOne("SELECT * FROM post_comment WHERE author_id = ? AND client_request_id = ?",
                commentRowMapper(), authorId, clientRequestId);
    }

    Optional<Comment> findCommentByRequestForShare(long authorId, String clientRequestId) {
        return queryOne("SELECT * FROM post_comment WHERE author_id = ? AND client_request_id = ? FOR SHARE",
                commentRowMapper(), authorId, clientRequestId);
    }

    Optional<Comment> lockComment(String businessKey, long postId) {
        return queryOne("SELECT * FROM post_comment WHERE business_key = ? AND post_id = ? FOR UPDATE",
                commentRowMapper(), businessKey, postId);
    }

    void insertComment(long id, String businessKey, long postId, long authorId, String clientRequestId,
            String requestHash, String content, LocalDateTime now) {
        jdbcTemplate.update("""
                INSERT INTO post_comment (id, business_key, post_id, author_id, client_request_id,
                    request_hash, content, created_at, updated_at, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, id, businessKey, postId, authorId, clientRequestId, requestHash, content, now, now);
    }

    void markCommentDeleted(long id, LocalDateTime now) {
        jdbcTemplate.update("UPDATE post_comment SET deleted = 1, updated_at = ? WHERE id = ?", now, id);
    }

    private <T> Optional<T> queryOne(String sql, RowMapper<T> rowMapper, Object... args) {
        List<T> rows = jdbcTemplate.query(sql, rowMapper, args);
        return rows.stream().findFirst();
    }

    private RowMapper<Post> postRowMapper() {
        return (rs, rowNum) -> new Post(rs.getLong("id"), rs.getString("business_key"),
                rs.getLong("author_id"), rs.getString("client_request_id"), rs.getString("request_hash"),
                rs.getString("content"), rs.getString("topic"), rs.getString("tags"),
                nullableLong(rs, "related_tea_id"), rs.getString("status"), rs.getLong("like_count"),
                rs.getLong("comment_count"), rs.getObject("published_at", LocalDateTime.class),
                rs.getObject("created_at", LocalDateTime.class), rs.getInt("deleted"));
    }

    private RowMapper<Comment> commentRowMapper() {
        return (rs, rowNum) -> new Comment(rs.getLong("id"), rs.getString("business_key"),
                rs.getLong("post_id"), rs.getLong("author_id"), rs.getString("client_request_id"),
                rs.getString("request_hash"), rs.getString("content"),
                rs.getObject("created_at", LocalDateTime.class), rs.getInt("deleted"));
    }

    private RowMapper<Media> mediaRowMapper() {
        return (rs, rowNum) -> mapMedia(rs);
    }

    private Media mapMedia(ResultSet rs) throws SQLException {
        return new Media(rs.getLong("id"), rs.getString("media_id"), rs.getLong("owner_user_id"),
                rs.getString("purpose"), rs.getString("media_type"), rs.getString("storage_provider"),
                rs.getString("external_url"), rs.getString("status"), rs.getString("bound_type"),
                rs.getString("bound_id"), rs.getInt("deleted"));
    }

    private RowMapper<Tea> teaRowMapper() {
        return (rs, rowNum) -> new Tea(rs.getLong("id"), rs.getString("business_key"), rs.getString("slug"),
                rs.getString("name"), rs.getString("aliases"), rs.getString("category"),
                rs.getString("origin_province"), rs.getString("origin_city"), rs.getString("origin_district"),
                rs.getString("summary"), nullableLong(rs, "cover_media_id"), rs.getString("cover_url"),
                rs.getBoolean("featured"), rs.getInt("sort"));
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
