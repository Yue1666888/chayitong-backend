package com.chayitong.tea;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chayitong.common.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("tea")
public class TeaEntity extends BaseEntity {
    private String businessKey;
    private String slug;
    private String name;
    private String aliases;
    private String category;
    private String originProvince;
    private String originCity;
    private String originDistrict;
    private String summary;
    private String processes;
    private String aromas;
    private String flavors;
    private String scenes;
    private String brewing;
    private String storageMethod;
    private String agingNote;
    private String drinkingNote;
    private String coverUrl;
    private String galleryMediaKeys;
    private String galleryUrls;
    private String contentBlocks;
    private String relatedProductKeys;
    private Boolean featured;
    private Integer sort;
    private String status;
    private LocalDateTime publishedAt;
}
