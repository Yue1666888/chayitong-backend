package com.chayitong.media;

import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.exceptions.BusinessException;
import com.chayitong.config.ChayitongProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class LocalMediaStorage implements MediaStorage {

    private final Path root;

    public LocalMediaStorage(ChayitongProperties properties) {
        String configuredRoot = properties.media() == null ? null : properties.media().localRoot();
        if (configuredRoot == null || configuredRoot.isBlank()) {
            throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE, "本地媒体目录未配置");
        }
        this.root = Path.of(configuredRoot).toAbsolutePath().normalize();
    }

    @Override
    public String provider() {
        return "LOCAL";
    }

    @Override
    public String bucket() {
        return null;
    }

    @Override
    public void put(Path source, String objectKey, String mimeType) throws IOException {
        Path destination = resolve(objectKey);
        Files.createDirectories(destination.getParent());
        Files.copy(source, destination);
    }

    @Override
    public InputStream open(String objectKey) throws IOException {
        return Files.newInputStream(resolve(objectKey));
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException | RuntimeException ignored) {
        }
    }

    private Path resolve(String objectKey) {
        Path path = root.resolve(objectKey).normalize();
        if (!path.startsWith(root)) {
            throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE);
        }
        return path;
    }
}
