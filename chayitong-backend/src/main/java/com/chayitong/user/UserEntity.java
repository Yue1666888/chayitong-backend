package com.chayitong.user;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chayitong.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("users")
public class UserEntity extends BaseEntity {
    private String businessKey;
    private String displayName;
    private Long avatarMediaId;
    private String bio;
    private String favoriteTea;
    private String role;
    private Long pointsBalance;
    private String status;
}
