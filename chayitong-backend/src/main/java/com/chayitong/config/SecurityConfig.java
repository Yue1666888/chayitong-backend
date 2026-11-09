package com.chayitong.config;

import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.api.TraceId;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, BearerTokenFilter bearerTokenFilter) {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(response, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, ErrorCode.FORBIDDEN)))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/auth/wechat/login", "/api/auth/refresh", "/api/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/recommendations").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/home", "/api/app-config", "/api/teas/**", "/api/articles/**",
                                "/api/search", "/api/quiz", "/api/recommendations/**", "/api/posts/**",
                                "/api/products/**", "/api/media/*/content").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(bearerTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    public static void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String traceId = MDC.get(TraceId.HEADER_NAME);
        String body = "{\"code\":\"" + errorCode.getCode() + "\",\"message\":\""
                + errorCode.getMessage() + "\",\"data\":null,\"timestamp\":\"" + Instant.now()
                + "\",\"traceId\":\"" + traceId + "\"}";
        response.getWriter().write(body);
    }
}
