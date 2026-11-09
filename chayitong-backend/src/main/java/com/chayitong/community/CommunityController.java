package com.chayitong.community;

import com.chayitong.common.api.CommonResponse;
import com.chayitong.common.api.PageResponse;
import com.chayitong.community.CommunityDtos.Comment;
import com.chayitong.community.CommunityDtos.CommentCreateResult;
import com.chayitong.community.CommunityDtos.CommentDeleteResult;
import com.chayitong.community.CommunityDtos.CreateCommentRequest;
import com.chayitong.community.CommunityDtos.CreatePostRequest;
import com.chayitong.community.CommunityDtos.LikeResult;
import com.chayitong.community.CommunityDtos.PostDetail;
import com.chayitong.community.CommunityDtos.PostSummary;
import com.chayitong.community.CommunityDtos.PostVisibilityRequest;
import com.chayitong.community.CommunityDtos.PostVisibilityResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommunityController {

    private final CommunityService service;

    CommunityController(CommunityService service) {
        this.service = service;
    }

    @GetMapping("/api/posts")
    public CommonResponse<PageResponse<PostSummary>> listPosts(
            @RequestParam(required = false) String topic,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return CommonResponse.success(service.listPosts(topic, pageNum, pageSize));
    }

    @GetMapping("/api/posts/{postId}")
    public CommonResponse<PostDetail> getPost(@PathVariable String postId) {
        return CommonResponse.success(service.getPublicPost(postId));
    }

    @PostMapping("/api/posts")
    public CommonResponse<PostDetail> createPost(@RequestBody CreatePostRequest request) {
        return CommonResponse.success(service.createPost(request));
    }

    @GetMapping("/api/me/posts")
    public CommonResponse<PageResponse<PostSummary>> myPosts(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return CommonResponse.success(service.listMyPosts(pageNum, pageSize));
    }

    @DeleteMapping("/api/posts/{postId}")
    public CommonResponse<Void> deletePost(@PathVariable String postId) {
        service.deletePost(postId);
        return CommonResponse.success();
    }

    @PutMapping("/api/posts/{postId}/like")
    public CommonResponse<LikeResult> like(@PathVariable String postId) {
        return CommonResponse.success(service.like(postId));
    }

    @DeleteMapping("/api/posts/{postId}/like")
    public CommonResponse<LikeResult> unlike(@PathVariable String postId) {
        return CommonResponse.success(service.unlike(postId));
    }

    @GetMapping("/api/posts/{postId}/comments")
    public CommonResponse<PageResponse<Comment>> comments(
            @PathVariable String postId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return CommonResponse.success(service.listComments(postId, pageNum, pageSize));
    }

    @PostMapping("/api/posts/{postId}/comments")
    public CommonResponse<CommentCreateResult> createComment(
            @PathVariable String postId, @RequestBody CreateCommentRequest request) {
        return CommonResponse.success(service.createComment(postId, request));
    }

    @DeleteMapping("/api/posts/{postId}/comments/{commentId}")
    public CommonResponse<CommentDeleteResult> deleteComment(
            @PathVariable String postId, @PathVariable String commentId) {
        return CommonResponse.success(service.deleteComment(postId, commentId));
    }

    @PutMapping("/api/admin/posts/{postId}/visibility")
    public CommonResponse<PostVisibilityResult> changeVisibility(
            @PathVariable String postId, @RequestBody PostVisibilityRequest request) {
        return CommonResponse.success(service.changeVisibility(postId, request));
    }
}
