package com.chayitong.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig implements MetaObjectHandler {

	@Bean
	public MybatisPlusInterceptor mybatisPlusInterceptor() {
		MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
		interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
		return interceptor;
	}

	@Override
	public void insertFill(MetaObject metaObject) {
		LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
		strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
		strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
		strictInsertFill(metaObject, "deleted", Integer.class, 0);
	}

	@Override
	public void updateFill(MetaObject metaObject) {
		metaObject.setValue("updatedAt", LocalDateTime.now(java.time.ZoneOffset.UTC));
	}
}
