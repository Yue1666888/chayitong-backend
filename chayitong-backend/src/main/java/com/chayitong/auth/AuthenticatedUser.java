package com.chayitong.auth;

public record AuthenticatedUser(
        Long id,
        String businessKey,
        String role,
        Long sessionId) {
}
