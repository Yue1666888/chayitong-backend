package com.chayitong.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    @Select("SELECT * FROM users WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    UserEntity lockById(@Param("id") Long id);
}
