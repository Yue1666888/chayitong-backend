package com.chayitong.chayitong_backend;

import java.time.LocalDateTime;
import java.util.List;

public record PostResponse(String id, String authorId, String content, LocalDateTime createdAt) {
  public record Page(int page, int pageSize, long total, List<PostResponse> items) {}
}
