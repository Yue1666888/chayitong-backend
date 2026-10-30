package com.chayitong.chayitong_backend;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 接收客户端 JSON 中的茶名和茶类。
public class TeaPreviewRequest {
  @NotBlank(message = "茶名不能为空")
  @Size(max = 50, message = "茶名长度不能超过 50")
  private String name;

  @NotBlank(message = "茶类不能为空")
  @Size(max = 20, message = "茶类长度不能超过 20")
  private String type;

  public TeaPreviewRequest() {
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }
}
