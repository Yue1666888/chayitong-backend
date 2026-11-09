package com.chayitong.auth;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuthSessionMapper extends BaseMapper<AuthSession> {

    @Select("SELECT * FROM auth_session WHERE refresh_token_hash = #{hash} AND deleted = 0 FOR UPDATE")
    AuthSession lockByRefreshTokenHash(@Param("hash") String hash);
}
