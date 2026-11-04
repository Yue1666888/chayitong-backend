package com.chayitong.chayitong_backend;

import jakarta.validation.constraints.Size;

public class ArticleFilterRequest {
  @Size(max = 32, message = "文章分类长度不能超过 32")
  private String category;

  @Size(max = 50, message = "搜索关键词长度不能超过 50")
  private String keyword;

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getKeyword() {
    return keyword;
  }

  public void setKeyword(String keyword) {
    this.keyword = keyword;
  }
}
