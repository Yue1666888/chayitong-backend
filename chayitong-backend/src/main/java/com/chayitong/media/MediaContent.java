package com.chayitong.media;

import java.io.InputStream;

public record MediaContent(InputStream inputStream, String mimeType, long contentLength, String externalUrl) {
}
