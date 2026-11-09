package com.chayitong.search;

import com.chayitong.article.ArticleModels.ArticleSummary;
import com.chayitong.common.api.PageResponse;
import com.chayitong.tea.TeaModels.TeaSummary;
import java.util.List;

public final class SearchModels {
    private SearchModels() {
    }

    public record ProductSummary(String id, String code, String categoryId, String primaryTeaId,
            List<String> relatedTeaIds, String name, String subtitle, String coverUrl,
            List<String> galleryUrls, List<String> tags, Boolean featured, Integer sort,
            String status, Long priceFen, Long originalPriceFen, String defaultSkuId) {
    }

    public record SearchResponse(PageResponse<ProductSummary> products, PageResponse<TeaSummary> teas,
            PageResponse<ArticleSummary> articles) {
    }

    record ProductCandidate(ProductSummary summary, String text) {
    }
}
