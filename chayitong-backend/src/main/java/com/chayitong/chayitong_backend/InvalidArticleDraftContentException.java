package com.chayitong.chayitong_backend;

public class InvalidArticleDraftContentException extends RuntimeException {
  public InvalidArticleDraftContentException(Throwable cause) {
    super("正文须为数组，每个内容块须符合对应类型的字段规则", cause);
  }
}
