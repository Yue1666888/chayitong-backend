package com.chayitong.chayitong_backend;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class TeaCreateRequest {
  @NotBlank(message = "茶叶编号不能为空")
  @Size(max = 20, message = "茶叶编号长度不能超过 20")
  @Pattern(regexp = "[A-Za-z0-9_-]+", message = "茶叶编号只能包含字母、数字、下划线和连字符")
  private String id;

  @NotBlank(message = "茶名不能为空")
  @Size(max = 50, message = "茶名长度不能超过 50")
  private String name;

  @NotBlank(message = "茶类不能为空")
  @Size(max = 20, message = "茶类长度不能超过 20")
  private String type;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
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
