package com.chayitong.chayitong_backend;

import java.time.LocalDateTime;

public class ArticlePublicationResponse {
  private final String id;
  private final String status;
  private final LocalDateTime publishedAt;

  public ArticlePublicationResponse(String id, String status, LocalDateTime publishedAt) {
    this.id = id;
    this.status = status;
    this.publishedAt = publishedAt;
  }

  public String getId() {
    return id;
  }

  public String getStatus() {
    return status;
  }

  public LocalDateTime getPublishedAt() {
    return publishedAt;
  }
}
