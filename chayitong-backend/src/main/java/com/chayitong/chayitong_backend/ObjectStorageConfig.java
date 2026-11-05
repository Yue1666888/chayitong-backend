package com.chayitong.chayitong_backend;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class ObjectStorageConfig {
  @Bean
  public S3Client s3Client(@Value("${app.storage.endpoint}") URI endpoint,
      @Value("${app.storage.access-key}") String accessKey,
      @Value("${app.storage.secret-key}") String secretKey) {
    return S3Client.builder()
        .endpointOverride(endpoint)
        .region(Region.US_EAST_1)
        .forcePathStyle(true)
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKey, secretKey)))
        .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(10)))
        .build();
  }
}
