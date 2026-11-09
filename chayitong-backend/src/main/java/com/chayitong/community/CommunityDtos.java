package com.chayitong.community;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.time.Instant;
import java.util.List;

public final class CommunityDtos {

    private CommunityDtos() {
    }

    public record CreatePostRequest(
            String clientRequestId,
            String content,
            String topic,
            List<String> tags,
            List<String> imageMediaIds,
            String videoMediaId,
            String relatedTeaId) {
        @JsonAnySetter
        void rejectUnknownField(String name, Object value) {
            throw new IllegalArgumentException("未知字段: " + name);
        }
    }

    public record CreateCommentRequest(String clientRequestId, String content) {
        @JsonAnySetter
        void rejectUnknownField(String name, Object value) {
            throw new IllegalArgumentException("未知字段: " + name);
        }
    }

    public record PostVisibilityRequest(String status) {
        @JsonAnySetter
        void rejectUnknownField(String name, Object value) {
            throw new IllegalArgumentException("未知字段: " + name);
        }
    }

    public record Level(
            int index,
            String name,
            String description,
            long points,
            String nextName,
            Long nextThreshold,
            long remainingPoints,
            int percent,
            boolean maxed) {
    }

    public record PostAuthor(String id, String displayName, String avatar, Level level) {
    }

    public record PostMedia(String mediaId, String url) {
    }

    public record TeaOrigin(String province, String city, String area) {
    }

    public record TeaSummary(
            String id,
            String slug,
            String name,
            List<String> aliases,
            String type,
            TeaOrigin origin,
            String summary,
            String cover,
            boolean featured,
            int sort) {
    }

    public record RelatedTea(boolean available, TeaSummary tea) {
    }

    public record PostSummary(
            String id,
            PostAuthor author,
            String content,
            String topic,
            List<String> tags,
            List<PostMedia> images,
            PostMedia video,
            RelatedTea relatedTea,
            long likeCount,
            long commentCount,
            boolean liked,
            String status,
            Instant publishedAt,
            Instant createdAt) {
    }

    public record PostDetail(
            String id,
            PostAuthor author,
            String content,
            String topic,
            List<String> tags,
            List<PostMedia> images,
            PostMedia video,
            RelatedTea relatedTea,
            long likeCount,
            long commentCount,
            boolean liked,
            String status,
            Instant publishedAt,
            Instant createdAt,
            List<Comment> comments) {
    }

    public record Comment(
            String id,
            String postId,
            PostAuthor author,
            String content,
            Instant createdAt) {
    }

    public record LikeResult(boolean liked, long likeCount) {
    }

    public record CommentCreateResult(Comment comment, long commentCount) {
    }

    public record CommentDeleteResult(boolean deleted, long commentCount) {
    }

    public record PostVisibilityResult(String id, String status) {
    }
}
