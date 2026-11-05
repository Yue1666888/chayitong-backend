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
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class ObjectStorageConfig {
  private final URI endpoint;
  private final StaticCredentialsProvider credentials;

  public ObjectStorageConfig(@Value("${app.storage.endpoint}") URI endpoint,
      @Value("${app.storage.access-key}") String accessKey,
      @Value("${app.storage.secret-key}") String secretKey) {
    this.endpoint = endpoint;
    this.credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
  }

  @Bean
  public S3Client s3Client() {
    return S3Client.builder()
        .endpointOverride(endpoint)
        .region(Region.US_EAST_1)
        .forcePathStyle(true)
        .credentialsProvider(credentials)
        .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(10)))
        .build();
  }

  @Bean
  public S3Presigner s3Presigner() {
    return S3Presigner.builder()
        .endpointOverride(endpoint)
        .region(Region.US_EAST_1)
        .credentialsProvider(credentials)
        .serviceConfiguration(S3Configuration.builder()
            .pathStyleAccessEnabled(true)
            .checksumValidationEnabled(false)
            .build())
        .build();
  }
}
