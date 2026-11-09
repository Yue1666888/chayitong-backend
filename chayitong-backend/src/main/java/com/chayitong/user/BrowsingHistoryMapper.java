package com.chayitong.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BrowsingHistoryMapper extends BaseMapper<BrowsingHistoryEntity> {

    @Select("SELECT * FROM browsing_history WHERE user_id = #{userId} AND target_type = #{type} AND target_id = #{targetId}")
    BrowsingHistoryEntity findIncludingDeleted(@Param("userId") Long userId, @Param("type") String type,
            @Param("targetId") String targetId);

    @Update("UPDATE browsing_history SET deleted = 1, updated_at = UTC_TIMESTAMP(6) WHERE id = #{id} AND deleted = 0")
    int softDeleteById(@Param("id") Long id);

    @Update("UPDATE browsing_history SET deleted = 1, updated_at = UTC_TIMESTAMP(6) WHERE user_id = #{userId} AND deleted = 0")
    int softDeleteAll(@Param("userId") Long userId);
}
