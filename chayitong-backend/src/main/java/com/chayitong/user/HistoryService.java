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
public class HistoryService {

    private static final int HISTORY_LIMIT = 50;

    private final BrowsingHistoryMapper historyMapper;
    private final UserMapper userMapper;
    private final TargetAccessService targetAccessService;

    public HistoryService(BrowsingHistoryMapper historyMapper, UserMapper userMapper,
            TargetAccessService targetAccessService) {
        this.historyMapper = historyMapper;
        this.userMapper = userMapper;
        this.targetAccessService = targetAccessService;
    }

    @Transactional
    public HistoryItem put(AuthenticatedUser user, TargetType type, String targetId) {
        if (targetId == null || targetId.isBlank() || targetAccessService.findPublic(type, targetId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (userMapper.lockById(user.id()) == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        BrowsingHistoryEntity history = historyMapper.findIncludingDeleted(user.id(), type.apiValue(), targetId);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (history == null) {
            history = new BrowsingHistoryEntity();
            history.setUserId(user.id());
            history.setTargetType(type.apiValue());
            history.setTargetId(targetId);
            history.setViewedAt(now);
            historyMapper.insert(history);
        } else {
            history.setDeleted(0);
            history.setViewedAt(now);
            historyMapper.updateById(history);
        }
        List<BrowsingHistoryEntity> active = historyMapper.selectList(Wrappers.<BrowsingHistoryEntity>lambdaQuery()
                .eq(BrowsingHistoryEntity::getUserId, user.id())
                .orderByDesc(BrowsingHistoryEntity::getViewedAt).orderByDesc(BrowsingHistoryEntity::getId));
        for (int i = HISTORY_LIMIT; i < active.size(); i++) {
            historyMapper.softDeleteById(active.get(i).getId());
        }
        return item(history);
    }

    @Transactional
    public void delete(AuthenticatedUser user, TargetType type, String targetId) {
        BrowsingHistoryEntity history = historyMapper.findIncludingDeleted(user.id(), type.apiValue(), targetId);
        if (history != null && history.getDeleted() == 0) {
            historyMapper.softDeleteById(history.getId());
        }
    }

    @Transactional
    public void clear(AuthenticatedUser user) {
        historyMapper.softDeleteAll(user.id());
    }

    public PageResponse<HistoryItem> list(AuthenticatedUser user, TargetType type, long pageNum, long pageSize) {
        Page<BrowsingHistoryEntity> page = new Page<>(pageNum, pageSize);
        var query = Wrappers.<BrowsingHistoryEntity>lambdaQuery().eq(BrowsingHistoryEntity::getUserId, user.id())
                .eq(type != null, BrowsingHistoryEntity::getTargetType, type == null ? null : type.apiValue())
                .orderByDesc(BrowsingHistoryEntity::getViewedAt).orderByDesc(BrowsingHistoryEntity::getId);
        historyMapper.selectPage(page, query);
        return PageResponse.of(page.getRecords().stream().map(this::item).toList(), page.getTotal(), pageNum, pageSize);
    }

    public long count(Long userId) {
        return historyMapper.selectCount(Wrappers.<BrowsingHistoryEntity>lambdaQuery()
                .eq(BrowsingHistoryEntity::getUserId, userId));
    }

    private HistoryItem item(BrowsingHistoryEntity history) {
        TargetType type = TargetType.parse(history.getTargetType());
        Map<String, Object> target = targetAccessService.findPublic(type, history.getTargetId());
        return new HistoryItem(history.getId().toString(), type.apiValue(), history.getTargetId(),
                history.getViewedAt().toInstant(ZoneOffset.UTC), target != null, target);
    }
}
