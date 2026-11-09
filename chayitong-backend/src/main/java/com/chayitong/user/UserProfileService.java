package com.chayitong.user;

import com.chayitong.auth.AuthenticatedUser;
import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.exceptions.BusinessException;
import com.chayitong.media.MediaFileEntity;
import com.chayitong.media.MediaService;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class UserProfileService {

    private static final Set<String> FAVORITE_TEAS = Set.of("绿茶", "白茶", "黄茶", "青茶", "红茶", "黑茶", "不挑，都喝");

    private final UserMapper userMapper;
    private final MediaService mediaService;

    public UserProfileService(UserMapper userMapper, MediaService mediaService) {
        this.userMapper = userMapper;
        this.mediaService = mediaService;
    }

    public UserProfile profile(AuthenticatedUser user) {
        UserEntity entity = userMapper.selectById(user.id());
        if (entity == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return profile(entity);
    }

    public UserProfile profile(UserEntity user) {
        long points = user.getPointsBalance() == null ? 0 : user.getPointsBalance();
        return new UserProfile(user.getBusinessKey(), user.getDisplayName(), mediaService.urlFor(user.getAvatarMediaId()),
                user.getBio(), user.getFavoriteTea(), user.getRole(), points, LevelInfo.of(points));
    }

    @Transactional
    public UserProfile update(AuthenticatedUser currentUser, JsonNode request) {
        if (request == null || !request.isObject()) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR);
        }
        boolean hasSupportedField = false;
        UserEntity user = userMapper.lockById(currentUser.id());
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (request.has("displayName")) {
            hasSupportedField = true;
            JsonNode value = request.get("displayName");
            if (!value.isTextual()) {
                throw new BusinessException(ErrorCode.PARAMETER_ERROR, "昵称格式不正确");
            }
            String displayName = value.asText().trim();
            if (displayName.isEmpty() || displayName.codePointCount(0, displayName.length()) > 12) {
                throw new BusinessException(ErrorCode.PARAMETER_ERROR, "昵称长度应为 1～12 个字符");
            }
            user.setDisplayName(displayName);
        }
        if (request.has("bio")) {
            hasSupportedField = true;
            JsonNode value = request.get("bio");
            if (value.isNull()) {
                user.setBio(null);
            } else if (value.isTextual()) {
                String bio = value.asText().trim();
                if (bio.codePointCount(0, bio.length()) > 24) {
                    throw new BusinessException(ErrorCode.PARAMETER_ERROR, "签名最多 24 个字符");
                }
                user.setBio(bio);
            } else {
                throw new BusinessException(ErrorCode.PARAMETER_ERROR, "签名格式不正确");
            }
        }
        if (request.has("favoriteTea")) {
            hasSupportedField = true;
            JsonNode value = request.get("favoriteTea");
            if (value.isNull()) {
                user.setFavoriteTea(null);
            } else if (value.isTextual()) {
                String favoriteTea = value.asText().trim();
                if (!favoriteTea.isEmpty() && !FAVORITE_TEAS.contains(favoriteTea)) {
                    throw new BusinessException(ErrorCode.PARAMETER_ERROR, "常喝茶类型不正确");
                }
                user.setFavoriteTea(favoriteTea.isEmpty() ? null : favoriteTea);
            } else {
                throw new BusinessException(ErrorCode.PARAMETER_ERROR, "常喝茶类型不正确");
            }
        }
        if (request.has("avatar")) {
            hasSupportedField = true;
            JsonNode value = request.get("avatar");
            if (value.isNull()) {
                unbindCurrentAvatar(user);
                user.setAvatarMediaId(null);
            } else if (value.isTextual() && !value.asText().isBlank()) {
                bindAvatar(user, value.asText());
            } else {
                throw new BusinessException(ErrorCode.PARAMETER_ERROR, "头像必须使用有效媒体 ID");
            }
        }
        if (!hasSupportedField) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "至少提供一个资料字段");
        }
        userMapper.updateById(user);
        return profile(user);
    }

    private void bindAvatar(UserEntity user, String mediaId) {
        MediaFileEntity current = user.getAvatarMediaId() == null ? null
                : mediaService.findOwnedAvatar(user.getAvatarMediaId());
        if (current != null && mediaId.equals(current.getMediaId())) {
            return;
        }
        MediaFileEntity replacement = mediaService.requireBindable(mediaId, user.getId(), "avatar");
        mediaService.bind(replacement, "user", user.getBusinessKey());
        unbindCurrentAvatar(user);
        user.setAvatarMediaId(replacement.getId());
    }

    private void unbindCurrentAvatar(UserEntity user) {
        if (user.getAvatarMediaId() != null) {
            MediaFileEntity current = mediaService.findOwnedAvatar(user.getAvatarMediaId());
            if (current != null && "BOUND".equals(current.getStatus())
                    && "user".equals(current.getBoundType()) && user.getBusinessKey().equals(current.getBoundId())) {
                mediaService.unbind(current);
            }
        }
    }
}
