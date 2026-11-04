package com.chayitong.chayitong_backend;

public class ArticleNotReadyException extends RuntimeException {
  public ArticleNotReadyException() {
    super("文章信息不完整或正文格式不正确，暂不能发布");
  }

  public ArticleNotReadyException(Throwable cause) {
    super("文章信息不完整或正文格式不正确，暂不能发布", cause);
  }
}
