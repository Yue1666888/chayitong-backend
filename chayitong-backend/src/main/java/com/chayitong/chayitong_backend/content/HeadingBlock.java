package com.chayitong.chayitong_backend.content;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class HeadingBlock implements ContentBlock {
  @NotNull(message = "标题级别不能为空")
  @Min(value = 1, message = "标题级别不能小于 1")
  @Max(value = 6, message = "标题级别不能大于 6")
  private Integer level;

  @NotBlank(message = "标题内容不能为空")
  private String text;

  @Override
  public String getType() {
    return "heading";
  }

  public Integer getLevel() {
    return level;
  }

  public void setLevel(Integer level) {
    this.level = level;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }
}
