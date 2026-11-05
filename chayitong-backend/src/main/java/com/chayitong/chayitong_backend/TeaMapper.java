package com.chayitong.chayitong_backend;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TeaMapper extends BaseMapper<TeaEntity> {
  @Select("SELECT cover_key FROM teas WHERE id = #{id}")
  String findCoverKey(@Param("id") String id);
}
