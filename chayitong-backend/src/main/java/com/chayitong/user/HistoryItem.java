package com.chayitong.user;

import java.time.Instant;
import java.util.Map;

public record HistoryItem(
        String id,
        String targetType,
        String targetId,
        Instant viewedAt,
        boolean available,
        Map<String, Object> target) {
}
