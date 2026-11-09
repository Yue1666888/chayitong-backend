package com.chayitong.recommendation;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chayitong.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("recommendation_result")
public class RecommendationResultEntity extends BaseEntity {
    private String resultId;
    private Long quizDefinitionId;
    private String quizVersion;
    private String answers;
    private String answersHash;
}
