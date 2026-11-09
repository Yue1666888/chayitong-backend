package com.chayitong.community;

import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.exceptions.BusinessException;
import com.chayitong.community.CommunityDtos.Comment;
import com.chayitong.community.CommunityDtos.Level;
import com.chayitong.community.CommunityDtos.PostAuthor;
import com.chayitong.community.CommunityDtos.PostDetail;
import com.chayitong.community.CommunityDtos.PostMedia;
import com.chayitong.community.CommunityDtos.PostSummary;
import com.chayitong.community.CommunityDtos.RelatedTea;
import com.chayitong.community.CommunityDtos.TeaOrigin;
import com.chayitong.community.CommunityDtos.TeaSummary;
import com.chayitong.community.CommunityRecords.Author;
import com.chayitong.community.CommunityRecords.Media;
import com.chayitong.community.CommunityRecords.Post;
import com.chayitong.community.CommunityRecords.Tea;
import com.chayitong.config.ChayitongProperties;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class CommunityAssembler {

    private static final long[] LEVEL_THRESHOLDS = {0, 100, 250, 450, 700, 1000, 1400, 2000, 3000};
    private static final String[] LEVEL_NAMES = {
            "初识茶味", "闻香识茶", "一盏知春", "茶席常客", "辨味识韵", "壶中岁月", "茶事精进", "通晓茶性", "茶中博士"
    };

    private final CommunityRepository repository;
    private final ObjectMapper objectMapper;
    private final String mediaBaseUrl;

    CommunityAssembler(CommunityRepository repository, ObjectMapper objectMapper, ChayitongProperties properties) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.mediaBaseUrl = properties.media().publicBaseUrl().replaceAll("/+$", "");
    }

    PostSummary toSummary(Post post, Long viewerId) {
        PostParts parts = parts(post, viewerId);
        return new PostSummary(post.businessKey(), parts.author(), post.content(), post.topic(), parts.tags(),
                parts.images(), parts.video(), parts.relatedTea(), post.likeCount(), post.commentCount(),
                parts.liked(), post.status(), instant(post.publishedAt()), instant(post.createdAt()));
    }

    PostDetail toDetail(Post post, Long viewerId) {
        PostParts parts = parts(post, viewerId);
        List<Comment> comments = repository.commentPreview(post.id()).stream().map(this::toComment).toList();
        return new PostDetail(post.businessKey(), parts.author(), post.content(), post.topic(), parts.tags(),
                parts.images(), parts.video(), parts.relatedTea(), post.likeCount(), post.commentCount(),
                parts.liked(), post.status(), instant(post.publishedAt()), instant(post.createdAt()), comments);
    }

    Comment toComment(CommunityRecords.Comment comment) {
        return new Comment(comment.businessKey(), findPostBusinessKey(comment.postId()), author(comment.authorId()),
                comment.content(), instant(comment.createdAt()));
    }

    private PostParts parts(Post post, Long viewerId) {
        List<PostMedia> images = new ArrayList<>();
        PostMedia video = null;
        for (CommunityRecords.PostMedia postMedia : repository.findPostMedia(post.id())) {
            Media media = postMedia.media();
            if (media.deleted() != 0) {
                continue;
            }
            PostMedia response = new PostMedia(media.mediaId(), mediaUrl(media));
            if ("VIDEO".equals(media.mediaType())) {
                video = response;
            } else {
                images.add(response);
            }
        }
        RelatedTea relatedTea = null;
        if (post.relatedTeaId() != null) {
            relatedTea = repository.findPublicTea(post.relatedTeaId())
                    .map(tea -> new RelatedTea(true, teaSummary(tea)))
                    .orElseGet(() -> new RelatedTea(false, null));
        }
        boolean liked = viewerId != null && repository.isLiked(post.id(), viewerId);
        return new PostParts(author(post.authorId()), stringList(post.tags()), List.copyOf(images), video,
                relatedTea, liked);
    }

    private PostAuthor author(long userId) {
        Author author = repository.findAuthor(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        String avatar = null;
        if (author.avatarMediaId() != null) {
            avatar = repository.findMediaById(author.avatarMediaId())
                    .filter(media -> media.deleted() == 0)
                    .map(this::mediaUrl)
                    .orElse(null);
        }
        return new PostAuthor(author.businessKey(), author.displayName(), avatar, level(author.points()));
    }

    private TeaSummary teaSummary(Tea tea) {
        String cover = tea.coverUrl();
        if (tea.coverMediaId() != null) {
            cover = repository.findMediaById(tea.coverMediaId())
                    .filter(media -> media.deleted() == 0)
                    .map(this::mediaUrl)
                    .orElse(null);
        }
        return new TeaSummary(tea.businessKey(), tea.slug(), tea.name(), stringList(tea.aliases()), tea.category(),
                new TeaOrigin(tea.originProvince(), tea.originCity(), tea.originDistrict()), tea.summary(), cover,
                tea.featured(), tea.sort());
    }

    private String findPostBusinessKey(long postId) {
        return repository.findPostById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND))
                .businessKey();
    }

    private String mediaUrl(Media media) {
        if (media.externalUrl() != null && !media.externalUrl().isBlank()) {
            return media.externalUrl();
        }
        return mediaBaseUrl + "/" + media.mediaId() + "/content";
    }

    private List<String> stringList(String json) {
        try {
            return List.copyOf(objectMapper.readValue(json, new TypeReference<List<String>>() {
            }));
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid JSON array", exception);
        }
    }

    private Level level(long points) {
        int index = 0;
        for (int i = 0; i < LEVEL_THRESHOLDS.length; i++) {
            if (points >= LEVEL_THRESHOLDS[i]) {
                index = i;
            }
        }
        boolean maxed = index == LEVEL_THRESHOLDS.length - 1;
        if (maxed) {
            return new Level(index + 1, LEVEL_NAMES[index], null, points, null, null, 0, 100, true);
        }
        long current = LEVEL_THRESHOLDS[index];
        long next = LEVEL_THRESHOLDS[index + 1];
        int percent = (int) ((points - current) * 100 / (next - current));
        return new Level(index + 1, LEVEL_NAMES[index], null, points, LEVEL_NAMES[index + 1], next,
                next - points, percent, false);
    }

    private Instant instant(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC);
    }

    private record PostParts(PostAuthor author, List<String> tags, List<PostMedia> images, PostMedia video,
            RelatedTea relatedTea, boolean liked) {
    }
}
