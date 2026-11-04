package com.chayitong.chayitong_backend.content;

import jakarta.validation.constraints.NotBlank;

public class TipBlock implements ContentBlock {
  private String tone;
  private String title;

  @NotBlank(message = "提示内容不能为空")
  private String text;

  @Override
  public String getType() {
    return "tip";
  }

  public String getTone() {
    return tone;
  }

  public void setTone(String tone) {
    this.tone = tone;
  }

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
