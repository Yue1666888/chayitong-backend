package com.chayitong.chayitong_backend;

import java.time.LocalDateTime;

// 文章列表只返回摘要字段，整篇正文由详情接口读取。
public class ArticleSummaryResponse {
  private final String id;
  private final String title;
  private final String summary;
  private final String category;
  private final String cover;
  private final LocalDateTime publishedAt;

  public ArticleSummaryResponse(String id, String title, String summary, String category,
      String cover, LocalDateTime publishedAt) {
    this.id = id;
    this.title = title;
    this.summary = summary;
    this.category = category;
    this.cover = cover;
    this.publishedAt = publishedAt;
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
}
