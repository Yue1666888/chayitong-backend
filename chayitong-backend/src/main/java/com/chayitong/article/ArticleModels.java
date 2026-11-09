package com.chayitong.article;

import com.chayitong.tea.TeaModels.TeaSummary;
import java.time.Instant;
import java.util.List;
import tools.jackson.databind.JsonNode;

public final class ArticleModels {
    private ArticleModels() {
    }

    public record ArticleRequest(String slug, String title, String summary, String category,
            List<String> tags, String cover, JsonNode contentBlocks, List<String> relatedTeaIds,
            Integer readMinutes, Long viewCountDisplay, Boolean featured, Integer sort) {
    }

    public record ArticleSummary(String id, String slug, String title, String summary,
            String category, List<String> tags, String cover, List<String> relatedTeaIds,
            Integer readMinutes, Long viewCountDisplay, Boolean featured, Integer sort,
            Instant publishedAt, String status, Instant createdAt, Instant updatedAt) {
    }

    public record ArticleDetail(String id, String slug, String title, String summary,
            String category, List<String> tags, String cover, List<String> relatedTeaIds,
            Integer readMinutes, Long viewCountDisplay, Boolean featured, Integer sort,
            Instant publishedAt, String status, Instant createdAt, Instant updatedAt,
            List<JsonNode> contentBlocks, List<TeaSummary> relatedTeas) {
    }
}
