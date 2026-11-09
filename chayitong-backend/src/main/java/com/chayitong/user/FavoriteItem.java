package com.chayitong.user;

import java.time.Instant;
import java.util.Map;

public record FavoriteItem(
        String id,
        String targetType,
        String targetId,
        boolean available,
        Instant createdAt,
        Map<String, Object> target) {
}
