package com.chayitong.chayitong_backend;

public class PublishedArticleDeleteException extends RuntimeException {
  public PublishedArticleDeleteException() {
    super("请先撤回发布，再删除文章");
  }
}
