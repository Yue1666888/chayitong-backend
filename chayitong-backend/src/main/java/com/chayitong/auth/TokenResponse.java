package com.chayitong.auth;

import com.chayitong.user.UserProfile;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long accessTokenExpiresIn,
        long refreshTokenExpiresIn,
        UserProfile user) {
}
