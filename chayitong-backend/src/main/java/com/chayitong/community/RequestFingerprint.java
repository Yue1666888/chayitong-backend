package com.chayitong.community;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

final class RequestFingerprint {

    private RequestFingerprint() {
    }

    static String sha256(List<String> parts) {
        StringBuilder canonical = new StringBuilder();
        for (String part : parts) {
            if (part == null) {
                canonical.append("-1:");
            } else {
                canonical.append(part.codePointCount(0, part.length())).append(':').append(part);
            }
            canonical.append('|');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
