package com.chayitong.auth;

public interface AccessTokenAuthenticator {

    AuthenticatedUser authenticate(String rawToken);
}
