package com.chayitong.chayitong_backend;

import jakarta.validation.constraints.Size;

// 搜索条件与 TeaPageRequest 的分页参数分别绑定，各自负责自己的字段。
public class TeaFilterRequest {
  private String type;

  @Size(max = 50, message = "搜索关键词长度不能超过 50")
  private String keyword;

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getKeyword() {
    return keyword;
  }

  public void setKeyword(String keyword) {
    this.keyword = keyword;
  }
}
