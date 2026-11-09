package com.chayitong.search;

import com.chayitong.article.ArticleEntity;
import com.chayitong.article.ArticleService;
import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.api.PageResponse;
import com.chayitong.common.exceptions.BusinessException;
import com.chayitong.content.PageParameters;
import com.chayitong.search.SearchModels.ProductCandidate;
import com.chayitong.search.SearchModels.ProductSummary;
import com.chayitong.search.SearchModels.SearchResponse;
import com.chayitong.tea.TeaEntity;
import com.chayitong.tea.TeaService;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

    private final TeaService teaService;
    private final ArticleService articleService;
    private final ProductSearchRepository productRepository;
    private final SearchMatcher matcher;

    public SearchService(TeaService teaService, ArticleService articleService,
            ProductSearchRepository productRepository, SearchMatcher matcher) {
        this.teaService = teaService;
        this.articleService = articleService;
        this.productRepository = productRepository;
        this.matcher = matcher;
    }

    public SearchResponse search(String keyword, PageParameters page) {
        if (keyword == null || keyword.isBlank()) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "搜索关键词不能为空");
        }
        String normalized = keyword.trim();
        List<TeaEntity> teas = teaService.publishedTeas().stream()
                .filter(tea -> matcher.matches(teaService.searchText(tea), normalized)).toList();
        List<ArticleEntity> articles = articleService.publishedArticles().stream()
                .filter(article -> matcher.matches(articleService.searchText(article), normalized)).toList();
        List<ProductCandidate> products = productRepository.findOnSale().stream()
                .filter(product -> matcher.matches(product.text(), normalized)).toList();
        return new SearchResponse(page(products, ProductCandidate::summary, page),
                page(teas, teaService::toSummary, page), page(articles, articleService::toSummary, page));
    }

    private <S, T> PageResponse<T> page(List<S> source, Function<S, T> mapper, PageParameters parameters) {
        int from = Math.min(parameters.offset(), source.size());
        int to = Math.min(from + parameters.pageSize(), source.size());
        return PageResponse.of(source.subList(from, to).stream().map(mapper).toList(), source.size(),
                parameters.pageNum(), parameters.pageSize());
    }
}
