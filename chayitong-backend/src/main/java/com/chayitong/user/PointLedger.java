package com.chayitong.user;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chayitong.common.entity.BaseEntity;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("point_ledger")
public class PointLedger extends BaseEntity {
    private Long userId;
    private String ruleCode;
    private LocalDate businessDate;
    private Integer deltaPoints;
    private Long balanceAfter;
    private String description;
}
