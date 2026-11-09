package com.chayitong.media;

import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.exceptions.BusinessException;
import com.chayitong.config.ChayitongProperties;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.region.Region;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class CosMediaStorage implements MediaStorage {

    private final ChayitongProperties.Cos configuration;
    private volatile COSClient client;

    @org.springframework.beans.factory.annotation.Autowired
    public CosMediaStorage(ChayitongProperties properties) {
        this.configuration = properties.media() == null ? null : properties.media().cos();
    }

    CosMediaStorage(ChayitongProperties.Cos configuration, COSClient client) {
        this.configuration = configuration;
        this.client = client;
    }

    @Override
    public String provider() {
        return "COS";
    }

    @Override
    public String bucket() {
        validateConfiguration();
        return configuration.bucket();
    }

    @Override
    public void put(Path source, String objectKey, String mimeType) throws IOException {
        var metadata = new com.qcloud.cos.model.ObjectMetadata();
        metadata.setContentType(mimeType);
        metadata.setContentLength(java.nio.file.Files.size(source));
        client().putObject(new com.qcloud.cos.model.PutObjectRequest(
                bucket(), key(objectKey), source.toFile()).withMetadata(metadata));
    }

    @Override
    public InputStream open(String objectKey) throws IOException {
        return client().getObject(new com.qcloud.cos.model.GetObjectRequest(bucket(), key(objectKey)))
                .getObjectContent();
    }

    @Override
    public void delete(String objectKey) {
        try {
            client().deleteObject(bucket(), key(objectKey));
        } catch (RuntimeException ignored) {
        }
    }

    private COSClient client() {
        validateConfiguration();
        COSClient result = client;
        if (result == null) {
            synchronized (this) {
                result = client;
                if (result == null) {
                    var credentials = new BasicCOSCredentials(configuration.secretId(), configuration.secretKey());
                    result = new COSClient(credentials, new ClientConfig(new Region(configuration.region())));
                    client = result;
                }
            }
        }
        return result;
    }

    private String key(String objectKey) {
        String prefix = configuration.prefix() == null ? "" : configuration.prefix().trim();
        if (!prefix.isEmpty() && !prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        return prefix + objectKey;
    }

    private void validateConfiguration() {
        if (configuration == null || isBlank(configuration.secretId()) || isBlank(configuration.secretKey())
                || isBlank(configuration.region()) || isBlank(configuration.bucket())) {
            throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE, "COS 媒体存储配置不完整");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
