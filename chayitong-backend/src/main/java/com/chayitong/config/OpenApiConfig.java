package com.chayitong.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI chayitongOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("茶一统后端 API")
                .version("1.0.0")
                .description("茶一统微信小程序内容、用户、社区和商城接口"));
    }
}
