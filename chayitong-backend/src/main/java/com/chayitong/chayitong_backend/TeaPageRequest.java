package com.chayitong.chayitong_backend;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

// 将 URL 中的 page、pageSize 查询参数绑定到一个对象中。
public class TeaPageRequest {
  @Min(value = 1, message = "页码不能小于 1")
  private int page = 1;

  @Min(value = 1, message = "每页数量不能小于 1")
  @Max(value = 100, message = "每页数量不能超过 100")
  private int pageSize = 10;

  public int getPage() {
    return page;
  }

  public void setPage(int page) {
    this.page = page;
  }

  public int getPageSize() {
    return pageSize;
  }

  public void setPageSize(int pageSize) {
    this.pageSize = pageSize;
  }
}
