package com.chayitong.recommendation;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chayitong.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("recommendation_item")
public class RecommendationItemEntity extends BaseEntity {
    private Long resultPk;
    private Long teaId;
    private String teaBusinessKey;
    private Integer rankNo;
    private Integer score;
    private Integer traitScore;
    private Integer matchPercent;
    private String reasons;
}
