package com.chayitong.content;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chayitong.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("app_config")
public class AppConfigEntity extends BaseEntity {
    private String configKey;
    private String configVersion;
    private String payload;
    private String activeQuizVersion;
    private Long revision;
    private Long updatedBy;
}
