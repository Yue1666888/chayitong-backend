package com.chayitong.media;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MediaFileMapper extends BaseMapper<MediaFileEntity> {

    @Select("SELECT * FROM media_file WHERE media_id = #{mediaId} AND deleted = 0 FOR UPDATE")
    MediaFileEntity lockByMediaId(@Param("mediaId") String mediaId);
}
