package com.chayitong.search;

import com.chayitong.common.api.CommonResponse;
import com.chayitong.content.PageParameters;
import com.chayitong.search.SearchModels.SearchResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/api/search")
    public CommonResponse<SearchResponse> search(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer pageNum, @RequestParam(required = false) Integer pageSize) {
        return CommonResponse.success(searchService.search(keyword, PageParameters.of(pageNum, pageSize)));
    }
}
