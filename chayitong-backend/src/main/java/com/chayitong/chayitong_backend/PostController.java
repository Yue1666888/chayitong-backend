package com.chayitong.chayitong_backend;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
public class PostController {
  private final PostService postService;

  public PostController(PostService postService) {
    this.postService = postService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PostResponse publish(@AuthenticationPrincipal LoginUser currentUser,
      @Valid @RequestBody PublishRequest request) {
    return postService.publish(currentUser.getUserId(), request.content());
  }

  @GetMapping
  public PostResponse.Page list(@Valid @ModelAttribute PageRequest pagination) {
    return postService.list(pagination.getPage(), pagination.getPageSize());
  }

  public record PublishRequest(
      @NotBlank(message = "帖子正文不能为空")
      @Size(max = 300, message = "帖子正文不能超过 300 个字符")
      String content) {}
}
