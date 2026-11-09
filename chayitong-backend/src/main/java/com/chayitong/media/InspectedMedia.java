package com.chayitong.media;

public record InspectedMedia(
        String mediaType,
        String mimeType,
        Integer width,
        Integer height,
        Integer durationMs) {
}
