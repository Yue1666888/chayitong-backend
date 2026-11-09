package com.chayitong.article;

import com.chayitong.article.ArticleModels.ArticleDetail;
import com.chayitong.article.ArticleModels.ArticleRequest;
import com.chayitong.article.ArticleModels.ArticleSummary;
import com.chayitong.common.api.CommonResponse;
import com.chayitong.common.api.PageResponse;
import com.chayitong.content.PageParameters;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ArticleController {

    private final ArticleService articleService;

    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @GetMapping("/api/articles")
    public CommonResponse<PageResponse<ArticleSummary>> list(@RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean featured, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer pageNum, @RequestParam(required = false) Integer pageSize) {
        return CommonResponse.success(articleService.listPublic(category, featured, keyword,
                PageParameters.of(pageNum, pageSize)));
    }

    @GetMapping("/api/articles/{id}")
    public CommonResponse<ArticleDetail> detail(@PathVariable String id) {
        return CommonResponse.success(articleService.getPublic(id));
    }

    @GetMapping("/api/admin/articles")
    public CommonResponse<PageResponse<ArticleSummary>> adminList(@RequestParam(required = false) String category,
            @RequestParam(required = false) String status, @RequestParam(required = false) Boolean featured,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        return CommonResponse.success(articleService.listAdmin(category, status, featured, keyword,
                PageParameters.of(pageNum, pageSize)));
    }

    @PostMapping("/api/admin/articles")
    public CommonResponse<ArticleDetail> create(@RequestBody ArticleRequest request) {
        return CommonResponse.success(articleService.create(request));
    }

    @GetMapping("/api/admin/articles/{id}")
    public CommonResponse<ArticleDetail> adminDetail(@PathVariable String id) {
        return CommonResponse.success(articleService.getAdmin(id));
    }

    @PutMapping("/api/admin/articles/{id}")
    public CommonResponse<ArticleDetail> update(@PathVariable String id, @RequestBody ArticleRequest request) {
        return CommonResponse.success(articleService.update(id, request));
    }

    @DeleteMapping("/api/admin/articles/{id}")
    public CommonResponse<Void> delete(@PathVariable String id) {
        articleService.delete(id);
        return CommonResponse.success();
    }

    @PutMapping("/api/admin/articles/{id}/publication")
    public CommonResponse<ArticleDetail> publish(@PathVariable String id) {
        return CommonResponse.success(articleService.publish(id));
    }

    @DeleteMapping("/api/admin/articles/{id}/publication")
    public CommonResponse<ArticleDetail> withdraw(@PathVariable String id) {
        return CommonResponse.success(articleService.withdraw(id));
    }
}
