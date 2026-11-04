package com.chayitong.chayitong_backend.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class ListBlock implements ContentBlock {
  @NotNull(message = "列表是否有序不能为空")
  private Boolean ordered;

  @NotEmpty(message = "列表条目不能为空")
  private List<@NotBlank(message = "列表条目不能为空白") String> items;

  @Override
  public String getType() {
    return "list";
  }

  public Boolean getOrdered() {
    return ordered;
  }

  public void setOrdered(Boolean ordered) {
    this.ordered = ordered;
  }

  public List<String> getItems() {
    return items;
  }

  public void setItems(List<String> items) {
    this.items = items;
  }
}
