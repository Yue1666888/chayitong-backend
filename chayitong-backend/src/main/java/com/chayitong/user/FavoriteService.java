package com.chayitong.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chayitong.auth.AuthenticatedUser;
import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.api.PageResponse;
import com.chayitong.common.exceptions.BusinessException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final TargetAccessService targetAccessService;
    private final UserMapper userMapper;

    public FavoriteService(FavoriteMapper favoriteMapper, TargetAccessService targetAccessService,
            UserMapper userMapper) {
        this.favoriteMapper = favoriteMapper;
        this.targetAccessService = targetAccessService;
        this.userMapper = userMapper;
    }

    @Transactional
    public FavoriteResult put(AuthenticatedUser user, TargetType type, String targetId) {
        requirePublic(type, targetId);
        if (userMapper.lockById(user.id()) == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        FavoriteEntity favorite = favoriteMapper.findIncludingDeleted(user.id(), type.apiValue(), targetId);
        if (favorite == null) {
            favorite = new FavoriteEntity();
            favorite.setUserId(user.id());
            favorite.setTargetType(type.apiValue());
            favorite.setTargetId(targetId);
            favorite.setActiveAt(LocalDateTime.now(ZoneOffset.UTC));
            favoriteMapper.insert(favorite);
        } else if (favorite.getDeleted() != 0) {
            favorite.setDeleted(0);
            favorite.setActiveAt(LocalDateTime.now(ZoneOffset.UTC));
            favoriteMapper.updateById(favorite);
        }
        return new FavoriteResult(true, countActive(user.id(), null));
    }

    @Transactional
    public FavoriteResult delete(AuthenticatedUser user, TargetType type, String targetId) {
        FavoriteEntity favorite = favoriteMapper.findIncludingDeleted(user.id(), type.apiValue(), targetId);
        if (favorite != null && favorite.getDeleted() == 0) {
            favoriteMapper.softDeleteById(favorite.getId());
        }
        return new FavoriteResult(false, countActive(user.id(), null));
    }

    public PageResponse<FavoriteItem> list(AuthenticatedUser user, TargetType type, long pageNum, long pageSize) {
        Page<FavoriteEntity> page = new Page<>(pageNum, pageSize);
        var query = Wrappers.<FavoriteEntity>lambdaQuery().eq(FavoriteEntity::getUserId, user.id())
                .eq(type != null, FavoriteEntity::getTargetType, type == null ? null : type.apiValue())
                .orderByDesc(FavoriteEntity::getActiveAt).orderByDesc(FavoriteEntity::getId);
        favoriteMapper.selectPage(page, query);
        List<FavoriteItem> items = page.getRecords().stream().map(this::item).toList();
        return PageResponse.of(items, countActive(user.id(), type), pageNum, pageSize);
    }

    public long countActive(Long userId, TargetType type) {
        return favoriteMapper.selectCount(Wrappers.<FavoriteEntity>lambdaQuery()
                .eq(FavoriteEntity::getUserId, userId)
                .eq(type != null, FavoriteEntity::getTargetType, type == null ? null : type.apiValue()));
    }

    private FavoriteItem item(FavoriteEntity favorite) {
        TargetType type = TargetType.parse(favorite.getTargetType());
        Map<String, Object> target = targetAccessService.findPublic(type, favorite.getTargetId());
        return new FavoriteItem(favorite.getId().toString(), type.apiValue(), favorite.getTargetId(), target != null,
                favorite.getActiveAt().toInstant(ZoneOffset.UTC), target);
    }

    private void requirePublic(TargetType type, String targetId) {
        if (targetId == null || targetId.isBlank() || targetAccessService.findPublic(type, targetId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }
}
