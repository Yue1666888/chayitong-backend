package com.chayitong.auth;

public interface WechatLoginClient {

    WechatIdentityData login(String code);

    record WechatIdentityData(String openid, String unionid) {
    }
}
