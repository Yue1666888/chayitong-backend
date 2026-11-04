package com.chayitong.chayitong_backend.content;

import jakarta.validation.constraints.NotBlank;

public class StepItem {
  @NotBlank(message = "步骤标题不能为空")
  private String title;

  @NotBlank(message = "步骤说明不能为空")
  private String text;

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }
}
