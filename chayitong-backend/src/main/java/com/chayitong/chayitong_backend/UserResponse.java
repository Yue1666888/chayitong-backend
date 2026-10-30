package com.chayitong.chayitong_backend;

// 小程序业务用户资料，与 MySQL 登录账号无关。
public class UserResponse {
  private final String id;
  private final String displayName;

  public UserResponse(String id, String displayName) {
    this.id = id;
    this.displayName = displayName;
  }

  public String getId() {
    return id;
  }

  public String getDisplayName() {
    return displayName;
  }
}
