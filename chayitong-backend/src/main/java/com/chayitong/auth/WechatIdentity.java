package com.chayitong.auth;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chayitong.common.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("wechat_identity")
public class WechatIdentity extends BaseEntity {
    private Long userId;
    private String appid;
    private String openid;
    private String unionid;
    private LocalDateTime lastLoginAt;
}
