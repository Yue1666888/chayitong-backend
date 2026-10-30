package com.chayitong.chayitong_backend;

import java.util.List;

public class TeaPageResponse {
  private final int page;
  private final int pageSize;
  private final long total;
  private final List<TeaResponse> items;

  public TeaPageResponse(int page, int pageSize, long total, List<TeaResponse> items) {
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

  public List<TeaResponse> getItems() {
    return items;
  }
}
