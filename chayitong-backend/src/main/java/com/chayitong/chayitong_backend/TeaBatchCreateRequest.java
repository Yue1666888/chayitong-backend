package com.chayitong.chayitong_backend;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public class TeaBatchCreateRequest {
  @NotEmpty(message = "茶叶列表不能为空")
  @Size(max = 20, message = "每批最多新增 20 款茶叶")
  @Valid
  private List<@NotNull(message = "茶叶数据不能为空") TeaCreateRequest> items;

  public List<TeaCreateRequest> getItems() {
    return items;
  }

  public void setItems(List<TeaCreateRequest> items) {
    this.items = items;
  }
}
