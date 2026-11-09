package com.chayitong.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record ChayitongProperties(
        Cors cors,
        Token token,
        Wechat wechat,
        Media media,
        Shop shop) {

    public record Cors(List<String> allowedOrigins) {
    }

    public record Token(long accessTtlSeconds, long refreshTtlSeconds) {
    }

    public record Wechat(String appId, String appSecret, String loginUrl) {
    }

    public record Media(String storage, String localRoot, String publicBaseUrl, S3 s3, Cos cos) {
    }

    public record S3(String endpoint, String region, String bucket, String accessKey, String secretKey) {
    }

    public record Cos(String secretId, String secretKey, String region, String bucket, String prefix) {
    }

    public record Shop(boolean demoPaymentEnabled, long quoteTtlSeconds, long paymentTtlSeconds, String quoteSecret) {
    }
}
