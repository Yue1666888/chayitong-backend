package com.chayitong.chayitong_backend;

import java.time.LocalDateTime;
import tools.jackson.databind.JsonNode;

public class ArticleDetailResponse {
  private final String id;
  private final String title;
  private final String summary;
  private final String category;
  private final String cover;
  private final LocalDateTime publishedAt;
  private final JsonNode contentBlocks;

  public ArticleDetailResponse(String id, String title, String summary, String category,
      String cover, LocalDateTime publishedAt, JsonNode contentBlocks) {
    this.id = id;
    this.title = title;
    this.summary = summary;
    this.category = category;
    this.cover = cover;
    this.publishedAt = publishedAt;
    this.contentBlocks = contentBlocks;
  }

  public String getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getSummary() {
    return summary;
  }

  public String getCategory() {
    return category;
  }

  public String getCover() {
    return cover;
  }

  public LocalDateTime getPublishedAt() {
    return publishedAt;
  }

  public JsonNode getContentBlocks() {
    return contentBlocks;
  }
}
