package com.chayitong.chayitong_backend;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TeaMapper extends BaseMapper<TeaEntity> {
  long countMatching(@Param("type") String type, @Param("namePattern") String namePattern);

  List<TeaResponse> findPage(@Param("type") String type,
      @Param("namePattern") String namePattern, @Param("limit") int limit,
      @Param("offset") long offset);
}
