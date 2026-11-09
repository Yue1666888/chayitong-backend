package com.chayitong.media;

import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.exceptions.BusinessException;
import com.chayitong.config.ChayitongProperties;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
public class S3MediaStorage implements MediaStorage {

    private final ChayitongProperties.S3 configuration;
    private volatile S3Client client;

    public S3MediaStorage(ChayitongProperties properties) {
        this.configuration = properties.media() == null ? null : properties.media().s3();
    }

    @Override
    public String provider() {
        return "S3";
    }

    @Override
    public String bucket() {
        validateConfiguration();
        return configuration.bucket();
    }

    @Override
    public void put(Path source, String objectKey, String mimeType) throws IOException {
        client().putObject(PutObjectRequest.builder().bucket(bucket()).key(objectKey).contentType(mimeType).build(),
                RequestBody.fromFile(source));
    }

    @Override
    public InputStream open(String objectKey) throws IOException {
        return client().getObject(GetObjectRequest.builder().bucket(bucket()).key(objectKey).build());
    }

    @Override
    public void delete(String objectKey) {
        try {
            client().deleteObject(DeleteObjectRequest.builder().bucket(bucket()).key(objectKey).build());
        } catch (RuntimeException ignored) {
        }
    }

    private S3Client client() {
        validateConfiguration();
        S3Client result = client;
        if (result == null) {
            synchronized (this) {
                result = client;
                if (result == null) {
                    var builder = S3Client.builder()
                            .region(Region.of(configuration.region()))
                            .credentialsProvider(StaticCredentialsProvider.create(
                                    AwsBasicCredentials.create(configuration.accessKey(), configuration.secretKey())))
                            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
                    if (!configuration.endpoint().isBlank()) {
                        builder.endpointOverride(URI.create(configuration.endpoint()));
                    }
                    client = builder.build();
                    result = client;
                }
            }
        }
        return result;
    }

    private void validateConfiguration() {
        if (configuration == null || isBlank(configuration.region()) || isBlank(configuration.bucket())
                || isBlank(configuration.accessKey()) || isBlank(configuration.secretKey())
                || configuration.endpoint() == null) {
            throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE, "S3 媒体存储配置不完整");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
