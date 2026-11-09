package com.chayitong.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FavoriteMapper extends BaseMapper<FavoriteEntity> {

    @Select("SELECT * FROM favorite WHERE user_id = #{userId} AND target_type = #{type} AND target_id = #{targetId}")
    FavoriteEntity findIncludingDeleted(@Param("userId") Long userId, @Param("type") String type,
            @Param("targetId") String targetId);

    @Update("UPDATE favorite SET deleted = 1, updated_at = UTC_TIMESTAMP(6) WHERE id = #{id} AND deleted = 0")
    int softDeleteById(@Param("id") Long id);
}
