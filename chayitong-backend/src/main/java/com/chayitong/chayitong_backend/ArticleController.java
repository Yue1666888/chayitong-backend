package com.chayitong.chayitong_backend;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ArticleController {
  private final ArticleService articleService;

  public ArticleController(ArticleService articleService) {
    this.articleService = articleService;
  }

  @GetMapping("/api/articles")
  public ArticlePageResponse list(@Valid @ModelAttribute ArticleFilterRequest filter,
      @Valid @ModelAttribute PageRequest pagination) {
    return articleService.listPublished(filter.getCategory(), filter.getKeyword(),
        pagination.getPage(), pagination.getPageSize());
  }

  @GetMapping("/api/articles/{id}")
  public ArticleDetailResponse detail(@PathVariable("id") String id) {
    ArticleDetailResponse article = articleService.findPublishedById(id);
    if (article == null) {
      // 草稿与不存在的编号返回相同的 404，不通过公开接口透露草稿信息。
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文章不存在");
    }
    return article;
  }
}
