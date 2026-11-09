package com.chayitong.article;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chayitong.common.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("article")
public class ArticleEntity extends BaseEntity {
    private String businessKey;
    private String slug;
    private String title;
    private String summary;
    private String category;
    private String tags;
    private String coverUrl;
    private String galleryMediaKeys;
    private String galleryUrls;
    private String contentBlocks;
    private String relatedTeaKeys;
    private Integer readMinutes;
    private Long viewCountDisplay;
    private Boolean featured;
    private Integer sort;
    private String status;
    private LocalDateTime publishedAt;
}
