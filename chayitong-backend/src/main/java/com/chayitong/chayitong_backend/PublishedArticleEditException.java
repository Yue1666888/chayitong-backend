package com.chayitong.chayitong_backend;

public class PublishedArticleEditException extends RuntimeException {
  public PublishedArticleEditException() {
    super("请先撤回发布，再编辑文章");
  }
}
