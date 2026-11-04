package com.chayitong.chayitong_backend;

// 配合 SQL 中的 ESCAPE '!'，生成按用户原文搜索的“包含”模式。
public final class SqlLikePattern {
  private SqlLikePattern() {
  }

  public static String contains(String keyword) {
    if (keyword == null) {
      return null;
    }
    String escaped = keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    return "%" + escaped + "%";
  }
}
