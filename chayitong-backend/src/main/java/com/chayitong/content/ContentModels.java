package com.chayitong.content;

import com.chayitong.article.ArticleModels.ArticleSummary;
import com.chayitong.tea.TeaModels.TeaSummary;
import java.util.List;
import tools.jackson.databind.JsonNode;

public final class ContentModels {
    private ContentModels() {
    }

    public record AppConfig(String id, Integer version, JsonNode seasonalTheme,
            List<JsonNode> banners, List<String> featuredTeaIds, List<String> featuredArticleIds,
            String announcement, JsonNode features, List<JsonNode> wikiTopics,
            List<String> wikiHeroArticleIds, List<String> searchHotKeywords) {
    }

    public record Home(AppConfig config, List<TeaSummary> featuredTeas,
            List<ArticleSummary> featuredArticles) {
    }
}
