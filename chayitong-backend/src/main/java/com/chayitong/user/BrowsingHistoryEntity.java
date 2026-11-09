package com.chayitong.user;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chayitong.common.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("browsing_history")
public class BrowsingHistoryEntity extends BaseEntity {
    private Long userId;
    private String targetType;
    private String targetId;
    private LocalDateTime viewedAt;
}
