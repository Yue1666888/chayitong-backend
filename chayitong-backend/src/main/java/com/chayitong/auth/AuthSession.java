package com.chayitong.auth;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chayitong.common.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("auth_session")
public class AuthSession extends BaseEntity {
    private Long userId;
    private String accessTokenHash;
    private String refreshTokenHash;
    private LocalDateTime accessExpiresAt;
    private LocalDateTime refreshExpiresAt;
    private LocalDateTime rotatedAt;
    private LocalDateTime revokedAt;
}
