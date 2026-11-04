package com.chayitong.chayitong_backend;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("teas")
public class TeaEntity {
  @TableId(value = "id", type = IdType.INPUT)
  private String id;
  private String name;
  private String type;
}
