package com.chayitong.chayitong_backend;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TeaMapper {
  TeaResponse findById(@Param("id") String id);
}
