package com.chayitong.chayitong_backend;

// 枚举明确区分三种业务结果，HTTP 状态码由控制器决定。
public enum FavoriteCreationResult {
  CREATED,
  ALREADY_EXISTS,
  RESOURCE_NOT_FOUND
}
