package com.chayitong.chayitong_backend.content;

public class InvalidArticleContentException extends RuntimeException {
  public InvalidArticleContentException() {
    super("文章正文不符合内容块模型");
  }

  public InvalidArticleContentException(Throwable cause) {
    super("文章正文不符合内容块模型", cause);
  }
}
