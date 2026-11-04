package com.chayitong.chayitong_backend.content;

import jakarta.validation.constraints.NotBlank;

public class QuoteBlock implements ContentBlock {
  @NotBlank(message = "引用内容不能为空")
  private String text;
  private String source;

  @Override
  public String getType() {
    return "quote";
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }
}
