package com.chayitong.chayitong_backend;

import com.chayitong.chayitong_backend.content.ContentBlock;
import com.chayitong.chayitong_backend.content.ContentBlockReader;
import com.chayitong.chayitong_backend.content.InvalidArticleContentException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class ArticleService {
  private static final ZoneId PUBLICATION_ZONE = ZoneId.of("Asia/Shanghai");
  private final ArticleRepository articleRepository;
  private final ContentBlockReader contentBlockReader;
  private final ObjectMapper objectMapper;

  public ArticleService(ArticleRepository articleRepository, ContentBlockReader contentBlockReader,
      ObjectMapper objectMapper) {
    this.articleRepository = articleRepository;
    this.contentBlockReader = contentBlockReader;
    this.objectMapper = objectMapper;
  }

  public ArticlePublicationResponse createDraft(ArticleDraftRequest request) {
    String contentJson = draftContentJson(request);
    String id = UUID.randomUUID().toString();
    articleRepository.insertDraft(id, request.title().strip(), request.category().strip(),
        request.summary() == null ? "" : request.summary(), request.cover(), contentJson);
    return new ArticlePublicationResponse(id, "draft", null);
  }

  @Transactional
  public boolean updateDraft(String id, ArticleDraftRequest request) {
    String contentJson = draftContentJson(request);
    String status = articleRepository.findStatusForUpdate(id);
    if (status == null) {
      return false;
    }
    if (!"draft".equals(status)) {
      throw new PublishedArticleEditException();
    }
    articleRepository.updateDraft(id, request.title().strip(), request.category().strip(),
        request.summary() == null ? "" : request.summary(), request.cover(), contentJson);
    return true;
  }

  private String draftContentJson(ArticleDraftRequest request) {
    List<ContentBlock> blocks;
    try {
      blocks = contentBlockReader.read(request.contentBlocks());
    } catch (InvalidArticleContentException exception) {
      throw new InvalidArticleDraftContentException(exception);
    }
    return objectMapper.writeValueAsString(blocks);
  }

  @Transactional(readOnly = true)
  public ArticlePageResponse listPublished(String category, String keyword, int page, int pageSize) {
    String categoryFilter = normalizedFilter(category);
    String keywordFilter = normalizedFilter(keyword);
    long offset = ((long) page - 1) * pageSize;
    long total = articleRepository.countPublished(categoryFilter, keywordFilter);
    return new ArticlePageResponse(page, pageSize, total,
        articleRepository.findPublished(categoryFilter, keywordFilter, pageSize, offset));
  }

  private String normalizedFilter(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.strip();
  }

  public ArticleDetailResponse findPublishedById(String id) {
    return articleRepository.findPublishedById(id);
  }

  public AdminArticleDetailResponse findAdminDetail(String id) {
    return articleRepository.findAdminDetail(id);
  }

  @Transactional
  public ArticlePublicationResponse publish(String id) {
    ArticleDetailResponse article;
    try {
      article = articleRepository.findForPublication(id);
    } catch (InvalidArticleContentException exception) {
      // 发布操作的前置条件不满足；公开 GET 对坏数据的处理仍是原来的 500。
      throw new ArticleNotReadyException(exception);
    }
    if (article == null) {
      return null;
    }
    if (article.getTitle().isBlank() || article.getCategory().isBlank()
        || article.getContentBlocks().isEmpty()) {
      throw new ArticleNotReadyException();
    }
    LocalDateTime publishedAt = LocalDateTime.now(PUBLICATION_ZONE).withNano(0);
    articleRepository.publishDraft(id, publishedAt);
    return articleRepository.findPublicationState(id);
  }

  @Transactional
  public boolean withdraw(String id) {
    return articleRepository.withdrawPublication(id) > 0 || articleRepository.existsById(id);
  }

  @Transactional
  public boolean deleteDraft(String id) {
    String status = articleRepository.findStatusForUpdate(id);
    if (status == null) {
      return false;
    }
    if (!"draft".equals(status)) {
      throw new PublishedArticleDeleteException();
    }
    articleRepository.deleteDraft(id);
    return true;
  }
}
