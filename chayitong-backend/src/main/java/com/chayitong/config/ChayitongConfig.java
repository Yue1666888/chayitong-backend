package com.chayitong.config;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = "com.chayitong", annotationClass = Mapper.class)
@EnableConfigurationProperties(ChayitongProperties.class)
public class ChayitongConfig {
}
