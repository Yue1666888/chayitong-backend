package com.chayitong.chayitong_backend;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArticleService {
  private final ArticleRepository articleRepository;

  public ArticleService(ArticleRepository articleRepository) {
    this.articleRepository = articleRepository;
  }

  @Transactional(readOnly = true)
  public ArticlePageResponse listPublished(int page, int pageSize) {
    long offset = ((long) page - 1) * pageSize;
    long total = articleRepository.countPublished();
    return new ArticlePageResponse(page, pageSize, total,
        articleRepository.findPublished(pageSize, offset));
  }

  public ArticleDetailResponse findPublishedById(String id) {
    return articleRepository.findPublishedById(id);
  }
}
