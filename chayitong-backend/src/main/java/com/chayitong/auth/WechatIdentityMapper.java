package com.chayitong.auth;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WechatIdentityMapper extends BaseMapper<WechatIdentity> {

    @Select("SELECT * FROM wechat_identity WHERE appid = #{appid} AND openid = #{openid} FOR UPDATE")
    WechatIdentity lockByAppidAndOpenid(@Param("appid") String appid, @Param("openid") String openid);
}
