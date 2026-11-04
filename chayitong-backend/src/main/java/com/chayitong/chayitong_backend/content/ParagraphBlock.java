package com.chayitong.chayitong_backend.content;

import jakarta.validation.constraints.NotBlank;

public class ParagraphBlock implements ContentBlock {
  @NotBlank(message = "段落正文不能为空")
  private String text;

  @Override
  public String getType() {
    return "paragraph";
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }
}
