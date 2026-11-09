package com.chayitong.media;

import java.time.Instant;

public record MediaResponse(
        String mediaId,
        String url,
        String mediaType,
        String mimeType,
        long sizeBytes,
        Integer width,
        Integer height,
        Integer durationSeconds,
        Instant createdAt) {
}
