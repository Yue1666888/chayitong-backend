package com.chayitong.user;

public record UserProfile(
        String id,
        String displayName,
        String avatar,
        String bio,
        String favoriteTea,
        String role,
        long points,
        LevelInfo level) {
}
