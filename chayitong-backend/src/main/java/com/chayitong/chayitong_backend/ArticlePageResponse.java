package com.chayitong.chayitong_backend;

import java.util.List;

public class ArticlePageResponse {
  private final int page;
  private final int pageSize;
  private final long total;
  private final List<ArticleSummaryResponse> items;

  public ArticlePageResponse(int page, int pageSize, long total, List<ArticleSummaryResponse> items) {
    this.page = page;
    this.pageSize = pageSize;
    this.total = total;
    this.items = items;
  }

  public int getPage() {
    return page;
  }

  public int getPageSize() {
    return pageSize;
  }

  public long getTotal() {
    return total;
  }

  public List<ArticleSummaryResponse> getItems() {
    return items;
  }
}
