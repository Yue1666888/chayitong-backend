package com.chayitong.chayitong_backend;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {
  @NotBlank(message = "登录名不能为空")
  @Size(min = 3, max = 32, message = "登录名长度需要在 3 到 32 个字符之间")
  @Pattern(regexp = "[A-Za-z0-9_]+", message = "登录名只能包含英文字母、数字和下划线")
  private String loginName;

  @NotBlank(message = "密码不能为空")
  @Size(min = 8, max = 64, message = "密码长度需要在 8 到 64 个字符之间")
  // 本节限制为 ASCII 可打印字符，确保不会超过 bcrypt 的 72 字节输入上限。
  @Pattern(regexp = "[\\x20-\\x7E]+", message = "本节密码支持英文字母、数字、英文符号和空格")
  private String password;

  @NotBlank(message = "昵称不能为空")
  @Size(max = 50, message = "昵称长度不能超过 50")
  private String displayName;

  public String getLoginName() {
    return loginName;
  }

  public void setLoginName(String loginName) {
    this.loginName = loginName;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }
}
