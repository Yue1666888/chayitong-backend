package com.chayitong.chayitong_backend;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("posts")
public class PostEntity {
  @TableId(type = IdType.AUTO)
  private Long id;
  private String authorId;
  private String content;
  private LocalDateTime createdAt;
}
