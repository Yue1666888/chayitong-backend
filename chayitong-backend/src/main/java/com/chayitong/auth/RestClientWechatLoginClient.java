package com.chayitong.auth;

import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.exceptions.BusinessException;
import com.chayitong.config.ChayitongProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

@Component
public class RestClientWechatLoginClient implements WechatLoginClient {

    private final ChayitongProperties properties;
    private final RestClient restClient;

    public RestClientWechatLoginClient(ChayitongProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(java.time.Duration.ofSeconds(5));
        requestFactory.setReadTimeout(java.time.Duration.ofSeconds(10));
        this.restClient = builder.requestFactory(requestFactory).build();
    }

    @Override
    public WechatIdentityData login(String code) {
        ChayitongProperties.Wechat wechat = properties.wechat();
        if (wechat == null || isBlank(wechat.appId()) || isBlank(wechat.appSecret())
                || isBlank(wechat.loginUrl())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "微信登录未配置");
        }
        JsonNode response;
        try {
            var uri = UriComponentsBuilder.fromUriString(wechat.loginUrl())
                    .queryParam("appid", wechat.appId())
                    .queryParam("secret", wechat.appSecret())
                    .queryParam("js_code", code)
                    .queryParam("grant_type", "authorization_code")
                    .build().toUri();
            response = restClient.get().uri(uri).retrieve().body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "微信身份服务不可用");
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "微信登录地址配置错误");
        }
        if (response == null || response.path("errcode").asInt(0) != 0
                || response.path("openid").asText().isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String unionid = response.path("unionid").asText(null);
        return new WechatIdentityData(response.path("openid").asText(), unionid);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
