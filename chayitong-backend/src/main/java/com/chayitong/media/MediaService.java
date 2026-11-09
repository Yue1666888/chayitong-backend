package com.chayitong.media;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chayitong.auth.AuthenticatedUser;
import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.exceptions.BusinessException;
import com.chayitong.config.ChayitongProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MediaService.class);
    private static final long IMAGE_LIMIT = 5L * 1024 * 1024;
    private static final long VIDEO_LIMIT = 20L * 1024 * 1024;
    private static final Set<String> PURPOSES = Set.of("avatar", "post", "content", "product");

    private final MediaFileMapper mediaFileMapper;
    private final MediaInspector mediaInspector;
    private final ChayitongProperties properties;
    private final Map<String, MediaStorage> storages;
    private final org.springframework.core.env.Environment environment;

    public MediaService(MediaFileMapper mediaFileMapper, MediaInspector mediaInspector,
            ChayitongProperties properties, List<MediaStorage> storageList,
            org.springframework.core.env.Environment environment) {
        this.mediaFileMapper = mediaFileMapper;
        this.mediaInspector = mediaInspector;
        this.properties = properties;
        this.storages = storageList.stream().collect(Collectors.toUnmodifiableMap(MediaStorage::provider,
                Function.identity()));
        this.environment = environment;
    }

    public MediaResponse upload(AuthenticatedUser user, MultipartFile multipart, String purpose) {
        if (purpose == null || !PURPOSES.contains(purpose)) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR);
        }
        if (("content".equals(purpose) || "product".equals(purpose)) && !"ADMIN".equals(user.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (multipart == null || multipart.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_FILE);
        }
        if (multipart.getSize() > IMAGE_LIMIT && !"video/mp4".equalsIgnoreCase(multipart.getContentType())) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
        }
        MediaStorage storage = storage();
        Path temporary = null;
        try {
            temporary = Files.createTempFile("chayitong-media-", ".upload");
            CopiedFile copied = copyWithLimit(multipart, temporary);
            InspectedMedia inspected = mediaInspector.inspect(temporary, multipart.getOriginalFilename(),
                    multipart.getContentType(), purpose);
            if (("VIDEO".equals(inspected.mediaType()) && copied.size() > VIDEO_LIMIT)
                    || ("IMAGE".equals(inspected.mediaType()) && copied.size() > IMAGE_LIMIT)) {
                throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
            }
            String extension = switch (inspected.mimeType()) {
                case "image/jpeg" -> ".jpg";
                case "image/png" -> ".png";
                case "image/webp" -> ".webp";
                case "video/mp4" -> ".mp4";
                default -> throw new BusinessException(ErrorCode.INVALID_FILE);
            };
            String objectKey = UUID.randomUUID() + extension;
            MediaFileEntity media = new MediaFileEntity();
            media.setMediaId(UUID.randomUUID().toString());
            media.setOwnerUserId(user.id());
            media.setPurpose(purpose);
            media.setMediaType(inspected.mediaType());
            media.setStorageProvider(storage.provider());
            media.setBucketName(storage.bucket());
            media.setObjectKey(objectKey);
            media.setOriginalFilename(originalFilename(multipart.getOriginalFilename()));
            media.setMimeType(inspected.mimeType());
            media.setSizeBytes(copied.size());
            media.setSha256(copied.sha256());
            media.setWidth(inspected.width());
            media.setHeight(inspected.height());
            media.setDurationMs(inspected.durationMs());
            media.setStatus("UPLOADED");
            boolean stored = false;
            try {
                storage.put(temporary, objectKey, inspected.mimeType());
                stored = true;
                mediaFileMapper.insert(media);
            } catch (IOException | RuntimeException exception) {
                log.warn("Media upload failed using {}", storage.provider(), exception);
                if (stored) {
                    storage.delete(objectKey);
                }
                if (exception instanceof BusinessException businessException) {
                    throw businessException;
                }
                throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE);
            }
            return response(media);
        } catch (RuntimeException exception) {
            log.warn("Media upload request failed", exception);
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
            }
        }
    }

    public MediaContent content(String mediaId) {
        MediaFileEntity media = mediaFileMapper.selectOne(Wrappers.<MediaFileEntity>lambdaQuery()
                .eq(MediaFileEntity::getMediaId, mediaId));
        if (media == null || "DELETED".equals(media.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        try {
            if ("EXTERNAL".equals(media.getStorageProvider())) {
                return new MediaContent(null, null, 0, controlledExternalUrl(media));
            }
            MediaStorage storage = storages.get(media.getStorageProvider());
            if (storage == null) {
                throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE);
            }
            return new MediaContent(storage.open(media.getObjectKey()), media.getMimeType(), media.getSizeBytes(), null);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE);
        }
    }

    @Transactional
    public MediaFileEntity requireBindable(String mediaId, Long ownerUserId, String purpose) {
        MediaFileEntity media = mediaFileMapper.lockByMediaId(mediaId);
        if (media == null) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "媒体不存在或不可绑定");
        }
        if (!ownerUserId.equals(media.getOwnerUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!purpose.equals(media.getPurpose()) || !"UPLOADED".equals(media.getStatus())
                || media.getBoundType() != null) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "媒体用途或状态不允许绑定");
        }
        return media;
    }

    @Transactional
    public void bind(MediaFileEntity media, String boundType, String boundId) {
        media.setStatus("BOUND");
        media.setBoundType(boundType);
        media.setBoundId(boundId);
        mediaFileMapper.updateById(media);
    }

    @Transactional
    public void unbind(MediaFileEntity media) {
        media.setStatus("UPLOADED");
        media.setBoundType(null);
        media.setBoundId(null);
        mediaFileMapper.updateById(media);
    }

    public MediaFileEntity findOwnedAvatar(Long internalId) {
        MediaFileEntity media = mediaFileMapper.selectById(internalId);
        return media != null && "avatar".equals(media.getPurpose()) ? media : null;
    }

    public String urlFor(Long internalId) {
        if (internalId == null) {
            return null;
        }
        MediaFileEntity media = mediaFileMapper.selectById(internalId);
        if (media == null || "DELETED".equals(media.getStatus())) {
            return null;
        }
        if ("EXTERNAL".equals(media.getStorageProvider())) {
            try {
                return controlledExternalUrl(media);
            } catch (BusinessException exception) {
                return null;
            }
        }
        return publicUrl(media.getMediaId());
    }

    private String controlledExternalUrl(MediaFileEntity media) {
        try {
            URI uri = URI.create(media.getExternalUrl());
            String host = uri.getHost();
            boolean controlled = host != null && (host.equals("myqcloud.com") || host.endsWith(".myqcloud.com")
                    || host.endsWith(".qcloudcdn.cn") || host.endsWith(".tencentcos.cn"));
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !controlled || uri.getUserInfo() != null) {
                throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE, "外部媒体地址不受信任");
            }
            return uri.toString();
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE, "外部媒体地址不合法");
        }
    }

    private MediaResponse response(MediaFileEntity media) {
        return new MediaResponse(media.getMediaId(), publicUrl(media.getMediaId()),
                media.getMediaType().toLowerCase(java.util.Locale.ROOT), media.getMimeType(), media.getSizeBytes(),
                media.getWidth(), media.getHeight(),
                media.getDurationMs() == null ? null : (media.getDurationMs() + 999) / 1000,
                media.getCreatedAt().toInstant(ZoneOffset.UTC));
    }

    private String publicUrl(String mediaId) {
        String base = properties.media().publicBaseUrl();
        if (base == null || base.isBlank()) {
            throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE, "媒体公开地址未配置");
        }
        return base.replaceAll("/+$", "") + "/" + mediaId + "/content";
    }

    private MediaStorage storage() {
        String configured = properties.media() == null ? null : properties.media().storage();
        if (configured == null) {
            throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE);
        }
        String provider = configured.toUpperCase(java.util.Locale.ROOT);
        if (!"LOCAL".equals(provider) && !"S3".equals(provider) && !"COS".equals(provider)) {
            throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE, "普通上传仅支持 LOCAL、S3 或 COS 存储");
        }
        MediaStorage storage = storages.get(provider);
        if (storage == null) {
            throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE, "不支持的媒体存储方式");
        }
        if ("LOCAL".equals(storage.provider()) && environment.acceptsProfiles("prod")) {
            throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE, "生产环境不允许使用本地媒体存储");
        }
        return storage;
    }

    private CopiedFile copyWithLimit(MultipartFile multipart, Path destination) throws IOException {
        long declaredSize = multipart.getSize();
        if (declaredSize > VIDEO_LIMIT) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
        long size = 0;
        byte[] buffer = new byte[8192];
        try (InputStream input = multipart.getInputStream(); OutputStream output = Files.newOutputStream(destination)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                size += read;
                if (size > VIDEO_LIMIT) {
                    throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }
        if (size == 0) {
            throw new BusinessException(ErrorCode.INVALID_FILE);
        }
        return new CopiedFile(size, HexFormat.of().formatHex(digest.digest()));
    }

    private String originalFilename(String filename) {
        if (filename == null) {
            return null;
        }
        String normalized = filename.replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1);
        return normalized.codePointCount(0, normalized.length()) > 255
                ? normalized.substring(0, normalized.offsetByCodePoints(0, 255)) : normalized;
    }

    private record CopiedFile(long size, String sha256) {
    }
}
