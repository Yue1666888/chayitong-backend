package com.chayitong.chayitong_backend.content;

import jakarta.validation.constraints.NotBlank;

public class ImageBlock implements ContentBlock {
  @NotBlank(message = "图片地址不能为空")
  private String src;
  private String alt;
  private String caption;

  @Override
  public String getType() {
    return "image";
  }

  public String getSrc() {
    return src;
  }

  public void setSrc(String src) {
    this.src = src;
  }

  public String getAlt() {
    return alt;
  }

  public void setAlt(String alt) {
    this.alt = alt;
  }

  public String getCaption() {
    return caption;
  }

  public void setCaption(String caption) {
    this.caption = caption;
  }
}
