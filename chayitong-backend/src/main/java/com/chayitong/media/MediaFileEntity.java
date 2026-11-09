package com.chayitong.media;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chayitong.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("media_file")
public class MediaFileEntity extends BaseEntity {
    private String mediaId;
    private Long ownerUserId;
    private String purpose;
    private String mediaType;
    private String storageProvider;
    private String externalUrl;
    private String bucketName;
    private String objectKey;
    private String originalFilename;
    private String mimeType;
    private Long sizeBytes;
    private String sha256;
    private Integer width;
    private Integer height;
    private Integer durationMs;
    private String status;
    private String boundType;
    private String boundId;
}
