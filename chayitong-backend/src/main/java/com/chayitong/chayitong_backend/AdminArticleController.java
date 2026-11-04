package com.chayitong.chayitong_backend;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AdminArticleController {
  private final ArticleService articleService;

  public AdminArticleController(ArticleService articleService) {
    this.articleService = articleService;
  }

  @GetMapping("/api/admin/articles/{id}")
  public AdminArticleDetailResponse detail(@PathVariable("id") String id) {
    AdminArticleDetailResponse result = articleService.findAdminDetail(id);
    if (result == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文章不存在");
    }
    return result;
  }

  @PostMapping(value = "/api/admin/articles", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ArticlePublicationResponse> createDraft(@Valid @RequestBody ArticleDraftRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(articleService.createDraft(request));
  }

  @PutMapping(value = "/api/admin/articles/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> updateDraft(
      @PathVariable("id") String id, @Valid @RequestBody ArticleDraftRequest request) {
    if (!articleService.updateDraft(id, request)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文章不存在");
    }
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/api/admin/articles/{id}/publication")
  public ArticlePublicationResponse publish(@PathVariable("id") String id) {
    ArticlePublicationResponse result = articleService.publish(id);
    if (result == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文章不存在");
    }
    return result;
  }

  @DeleteMapping("/api/admin/articles/{id}")
  public ResponseEntity<Void> deleteDraft(@PathVariable("id") String id) {
    if (!articleService.deleteDraft(id)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文章不存在");
    }
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/api/admin/articles/{id}/publication")
  public ResponseEntity<Void> withdraw(@PathVariable("id") String id) {
    if (!articleService.withdraw(id)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文章不存在");
    }
    return ResponseEntity.noContent().build();
  }
}
