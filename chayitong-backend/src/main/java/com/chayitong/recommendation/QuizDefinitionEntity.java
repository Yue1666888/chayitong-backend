package com.chayitong.recommendation;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chayitong.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("quiz_definition")
public class QuizDefinitionEntity extends BaseEntity {
    private String quizVersion;
    private String questions;
    private String scoringRules;
    private String status;
}
