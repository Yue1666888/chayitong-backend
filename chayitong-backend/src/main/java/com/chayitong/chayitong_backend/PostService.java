package com.chayitong.chayitong_backend;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {
  private final PostMapper postMapper;
  private final ApplicationEventPublisher events;

  public PostService(PostMapper postMapper, ApplicationEventPublisher events) {
    this.postMapper = postMapper;
    this.events = events;
  }

  @Transactional
  public PostResponse publish(String authorId, String content) {
    PostEntity post = new PostEntity();
    post.setAuthorId(authorId);
    post.setContent(content.strip());
    postMapper.insert(post);
    // 回查由数据库生成的发布时间。
    PostResponse response = toResponse(postMapper.selectById(post.getId()));
    events.publishEvent(new PostPublishedEvent(response.id()));
    return response;
  }

  @Transactional(readOnly = true)
  public PostResponse.Page list(int page, int pageSize) {
    var order = Wrappers.<PostEntity>lambdaQuery()
        .orderByDesc(PostEntity::getCreatedAt)
        .orderByDesc(PostEntity::getId);
    Page<PostEntity> result = postMapper.selectPage(new Page<>(page, pageSize), order);
    List<PostResponse> items = result.getRecords().stream().map(this::toResponse).toList();
    return new PostResponse.Page(page, pageSize, result.getTotal(), items);
  }

  private PostResponse toResponse(PostEntity post) {
    return new PostResponse(post.getId().toString(), post.getAuthorId(),
        post.getContent(), post.getCreatedAt());
  }
}
