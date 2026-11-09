package com.chayitong.community;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.chayitong.auth.AuthenticatedUser;
import com.chayitong.auth.CurrentUser;
import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.api.PageResponse;
import com.chayitong.common.exceptions.BusinessException;
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
import com.chayitong.community.CommunityRecords.Media;
import com.chayitong.community.CommunityRecords.Post;
import com.chayitong.community.CommunityRecords.Tea;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunityService {

    private static final Set<String> TOPICS = Set.of("tasting", "brewing", "teaware", "daily", "question");
    private static final int MAX_PAGE_SIZE = 100;

    private final CommunityRepository repository;
    private final CommunityAssembler assembler;
    private final ObjectMapper objectMapper;

    CommunityService(CommunityRepository repository, CommunityAssembler assembler, ObjectMapper objectMapper) {
        this.repository = repository;
        this.assembler = assembler;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<PostSummary> listPosts(String topic, int pageNum, int pageSize) {
        String normalizedTopic = normalizeTopicFilter(topic);
        PageWindow page = page(pageNum, pageSize);
        List<PostSummary> posts = repository.listPublicPosts(normalizedTopic, pageSize, page.offset()).stream()
                .map(post -> assembler.toSummary(post, currentUserId())).toList();
        return PageResponse.of(posts, repository.countPublicPosts(normalizedTopic), pageNum, pageSize);
    }

    @Transactional(readOnly = true)
    public PostDetail getPublicPost(String postId) {
        Post post = repository.findPublicPost(postId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return assembler.toDetail(post, currentUserId());
    }

    @Transactional
    public PostDetail createPost(CreatePostRequest request) {
        AuthenticatedUser user = CurrentUser.require();
        NormalizedPost normalized = normalizePost(request);
        String fingerprint = postFingerprint(normalized);
        var existing = repository.findPostByRequest(user.id(), normalized.clientRequestId());
        if (existing.isPresent()) {
            return existingPost(existing.get(), fingerprint, user.id());
        }

        Long relatedTeaId = null;
        if (normalized.relatedTeaKey() != null) {
            relatedTeaId = repository.findPublicTea(normalized.relatedTeaKey())
                    .map(Tea::id)
                    .orElseThrow(() -> badRequest("关联茶不存在或未发布"));
        }

        long id = IdWorker.getId();
        String businessKey = IdWorker.getIdStr();
        LocalDateTime now = utcNow();
        try {
            repository.insertPost(id, businessKey, user.id(), normalized.clientRequestId(), fingerprint,
                    normalized.content(), normalized.topic(), writeJson(normalized.tags()), relatedTeaId, now);
        } catch (DuplicateKeyException exception) {
            Post duplicate = repository.findPostByRequestForShare(user.id(), normalized.clientRequestId())
                    .orElseThrow(() -> exception);
            return existingPost(duplicate, fingerprint, user.id());
        }

        bindPostMedia(id, businessKey, user.id(), normalized, now);
        Post created = repository.findPostById(id).orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR));
        return assembler.toDetail(created, user.id());
    }

    @Transactional(readOnly = true)
    public PageResponse<PostSummary> listMyPosts(int pageNum, int pageSize) {
        long userId = CurrentUser.require().id();
        PageWindow page = page(pageNum, pageSize);
        List<PostSummary> posts = repository.listMyPosts(userId, pageSize, page.offset()).stream()
                .map(post -> assembler.toSummary(post, userId)).toList();
        return PageResponse.of(posts, repository.countMyPosts(userId), pageNum, pageSize);
    }

    @Transactional
    public void deletePost(String postId) {
        long userId = CurrentUser.require().id();
        Post post = repository.lockPost(postId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (post.authorId() != userId) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (post.deleted() == 0 && !"deleted".equals(post.status())) {
            repository.markPostDeleted(post.id(), utcNow());
        }
    }

    @Transactional
    public LikeResult like(String postId) {
        long userId = CurrentUser.require().id();
        Post post = lockInteractivePost(postId);
        LocalDateTime now = utcNow();
        var like = repository.lockLike(post.id(), userId);
        if (like.isEmpty()) {
            repository.insertLike(IdWorker.getId(), post.id(), userId, now);
            repository.incrementLikeCount(post.id(), now);
            return new LikeResult(true, post.likeCount() + 1);
        }
        if (like.get().deleted() != 0) {
            repository.restoreLike(like.get().id(), now);
            repository.incrementLikeCount(post.id(), now);
            return new LikeResult(true, post.likeCount() + 1);
        }
        return new LikeResult(true, post.likeCount());
    }

    @Transactional
    public LikeResult unlike(String postId) {
        long userId = CurrentUser.require().id();
        Post post = lockInteractivePost(postId);
        var like = repository.lockLike(post.id(), userId);
        if (like.isPresent() && like.get().deleted() == 0) {
            LocalDateTime now = utcNow();
            repository.removeLike(like.get().id(), now);
            repository.decrementLikeCount(post.id(), now);
            return new LikeResult(false, Math.max(0, post.likeCount() - 1));
        }
        return new LikeResult(false, post.likeCount());
    }

    @Transactional(readOnly = true)
    public PageResponse<Comment> listComments(String postId, int pageNum, int pageSize) {
        Post post = repository.findPublicPost(postId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        PageWindow page = page(pageNum, pageSize);
        List<Comment> comments = repository.listComments(post.id(), pageSize, page.offset()).stream()
                .map(assembler::toComment).toList();
        return PageResponse.of(comments, repository.countComments(post.id()), pageNum, pageSize);
    }

    @Transactional
    public CommentCreateResult createComment(String postId, CreateCommentRequest request) {
        long userId = CurrentUser.require().id();
        String clientRequestId = normalizeClientRequestId(request.clientRequestId());
        String content = normalizeText(request.content(), 200, "评论内容");
        String fingerprint = RequestFingerprint.sha256(List.of(postId, content));
        var existing = repository.findCommentByRequest(userId, clientRequestId);
        if (existing.isPresent()) {
            return existingComment(existing.get(), fingerprint);
        }

        Post post = lockInteractivePost(postId);
        long id = IdWorker.getId();
        LocalDateTime now = utcNow();
        try {
            repository.insertComment(id, IdWorker.getIdStr(), post.id(), userId, clientRequestId, fingerprint,
                    content, now);
        } catch (DuplicateKeyException exception) {
            CommunityRecords.Comment duplicate = repository.findCommentByRequestForShare(userId, clientRequestId)
                    .orElseThrow(() -> exception);
            return existingComment(duplicate, fingerprint);
        }
        repository.incrementCommentCount(post.id(), now);
        CommunityRecords.Comment created = repository.findCommentByRequest(userId, clientRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR));
        return new CommentCreateResult(assembler.toComment(created), post.commentCount() + 1);
    }

    @Transactional
    public CommentDeleteResult deleteComment(String postId, String commentId) {
        long userId = CurrentUser.require().id();
        Post post = repository.lockPost(postId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        CommunityRecords.Comment comment = repository.lockComment(commentId, post.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (comment.authorId() != userId) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (comment.deleted() == 0) {
            LocalDateTime now = utcNow();
            repository.markCommentDeleted(comment.id(), now);
            repository.decrementCommentCount(post.id(), now);
            return new CommentDeleteResult(true, Math.max(0, post.commentCount() - 1));
        }
        return new CommentDeleteResult(true, post.commentCount());
    }

    @Transactional
    public PostVisibilityResult changeVisibility(String postId, PostVisibilityRequest request) {
        AuthenticatedUser user = CurrentUser.require();
        if (!"ADMIN".equals(user.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        String status = request.status() == null ? "" : request.status().trim();
        if (!"hidden".equals(status) && !"published".equals(status)) {
            throw badRequest("动态状态只能为 hidden 或 published");
        }
        Post post = repository.lockPost(postId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (post.deleted() != 0 || "deleted".equals(post.status())) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "已删除动态不能恢复");
        }
        if (!status.equals(post.status())) {
            repository.updatePostStatus(post.id(), status, utcNow());
        }
        return new PostVisibilityResult(post.businessKey(), status);
    }

    private PostDetail existingPost(Post post, String fingerprint, long viewerId) {
        if (!post.requestHash().equals(fingerprint)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return assembler.toDetail(post, viewerId);
    }

    private CommentCreateResult existingComment(CommunityRecords.Comment comment, String fingerprint) {
        if (!comment.requestHash().equals(fingerprint)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        Post post = repository.lockPostById(comment.postId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return new CommentCreateResult(assembler.toComment(comment), post.commentCount());
    }

    private Post lockInteractivePost(String postId) {
        Post post = repository.lockPost(postId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (post.deleted() != 0 || !"published".equals(post.status())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return post;
    }

    private void bindPostMedia(long postId, String postBusinessKey, long userId, NormalizedPost request,
            LocalDateTime now) {
        int sortOrder = 0;
        for (String mediaId : request.imageMediaIds()) {
            Media media = lockBindableMedia(mediaId, userId, "IMAGE");
            repository.bindMedia(media.id(), postBusinessKey, now);
            repository.insertPostMedia(IdWorker.getId(), postId, media.id(), "IMAGE", sortOrder++, now);
        }
        if (request.videoMediaId() != null) {
            Media media = lockBindableMedia(request.videoMediaId(), userId, "VIDEO");
            repository.bindMedia(media.id(), postBusinessKey, now);
            repository.insertPostMedia(IdWorker.getId(), postId, media.id(), "VIDEO", 0, now);
        }
    }

    private Media lockBindableMedia(String mediaId, long userId, String expectedType) {
        Media media = repository.lockMedia(mediaId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (media.ownerUserId() != userId) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (media.deleted() != 0 || !"post".equals(media.purpose()) || !expectedType.equals(media.mediaType())
                || !"UPLOADED".equals(media.status()) || media.boundType() != null || media.boundId() != null) {
            throw badRequest("媒体不可绑定到当前动态");
        }
        return media;
    }

    private NormalizedPost normalizePost(CreatePostRequest request) {
        String clientRequestId = normalizeClientRequestId(request.clientRequestId());
        String content = normalizeText(request.content(), 300, "动态正文");
        String topic = request.topic() == null ? "" : request.topic().trim();
        if (!TOPICS.contains(topic)) {
            throw badRequest("非法动态话题");
        }
        List<String> tags = normalizeTags(request.tags());
        List<String> images = normalizeMediaIds(request.imageMediaIds());
        if (images.size() > 9) {
            throw badRequest("动态最多包含 9 张图片");
        }
        String video = optionalId(request.videoMediaId(), "视频媒体 ID");
        if (video != null && !images.isEmpty()) {
            throw badRequest("图片与视频不能同时发布");
        }
        String relatedTeaKey = optionalId(request.relatedTeaId(), "关联茶 ID");
        return new NormalizedPost(clientRequestId, content, topic, tags, images, video, relatedTeaKey);
    }

    private String normalizeTopicFilter(String topic) {
        if (topic == null || topic.isBlank() || "all".equals(topic.trim())) {
            return null;
        }
        String normalized = topic.trim();
        if (!TOPICS.contains(normalized)) {
            throw badRequest("非法动态话题");
        }
        return normalized;
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        if (tags.size() > 5) {
            throw badRequest("动态最多包含 5 个标签");
        }
        List<String> normalized = new ArrayList<>(tags.size());
        for (String tag : tags) {
            normalized.add(normalizeText(tag, 20, "标签"));
        }
        return List.copyOf(normalized);
    }

    private List<String> normalizeMediaIds(List<String> mediaIds) {
        if (mediaIds == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>(mediaIds.size());
        Set<String> unique = new HashSet<>();
        for (String mediaId : mediaIds) {
            String value = optionalId(mediaId, "图片媒体 ID");
            if (value == null || !unique.add(value)) {
                throw badRequest("图片媒体 ID 不能为空或重复");
            }
            normalized.add(value);
        }
        return List.copyOf(normalized);
    }

    private String normalizeClientRequestId(String value) {
        return normalizeText(value, 64, "clientRequestId");
    }

    private String normalizeText(String value, int maxCodePoints, String field) {
        if (value == null) {
            throw badRequest(field + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > maxCodePoints) {
            throw badRequest(field + "长度不合法");
        }
        return normalized;
    }

    private String optionalId(String value, String field) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > 64) {
            throw badRequest(field + "不合法");
        }
        return normalized;
    }

    private String postFingerprint(NormalizedPost post) {
        List<String> parts = new ArrayList<>();
        parts.add(post.content());
        parts.add(post.topic());
        parts.add(Integer.toString(post.tags().size()));
        parts.addAll(post.tags());
        parts.add(Integer.toString(post.imageMediaIds().size()));
        parts.addAll(post.imageMediaIds());
        parts.add(post.videoMediaId());
        parts.add(post.relatedTeaKey());
        return RequestFingerprint.sha256(parts);
    }

    private String writeJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private PageWindow page(int pageNum, int pageSize) {
        if (pageNum < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw badRequest("分页参数不合法");
        }
        long offset = (long) (pageNum - 1) * pageSize;
        if (offset > Integer.MAX_VALUE) {
            throw badRequest("分页参数不合法");
        }
        return new PageWindow((int) offset);
    }

    private Long currentUserId() {
        AuthenticatedUser user = CurrentUser.get();
        return user == null ? null : user.id();
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.PARAMETER_ERROR, message);
    }

    private record NormalizedPost(String clientRequestId, String content, String topic, List<String> tags,
            List<String> imageMediaIds, String videoMediaId, String relatedTeaKey) {
    }

    private record PageWindow(int offset) {
    }
}
