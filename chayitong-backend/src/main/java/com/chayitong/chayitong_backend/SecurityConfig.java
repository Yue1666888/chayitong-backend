package com.chayitong.chayitong_backend;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public DaoAuthenticationProvider authenticationProvider(DatabaseUserDetailsService users,
      PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
    provider.setPasswordEncoder(passwordEncoder);
    return provider;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http,
      DaoAuthenticationProvider provider, SecurityJsonResponse json) throws Exception {
    AccessDeniedHandler denied = (request, response, exception) -> {
      boolean csrfFailure = exception instanceof CsrfException;
      json.error(response, 403, csrfFailure ? "CSRF_FAILED" : "FORBIDDEN",
          csrfFailure ? "请求校验失败，请重新获取 CSRF 校验值" : "当前账号无权执行此操作");
    };

    http.authenticationProvider(provider)
        .authorizeHttpRequests(rules -> rules
            .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
            .requestMatchers("/api/auth/csrf", "/api/auth/login", "/api/health", "/api/hello").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
            .requestMatchers("/api/auth/me", "/api/database/check").authenticated()
            .requestMatchers(HttpMethod.GET, "/api/me/favorites").authenticated()
            .requestMatchers(HttpMethod.PUT, "/api/me/favorites/{teaId}").authenticated()
            .requestMatchers(HttpMethod.DELETE, "/api/me/favorites/{teaId}").authenticated()
            .requestMatchers("/api/users/{userId}/**").access((authentication, context) -> {
              var current = authentication.get();
              boolean owner = current != null && current.isAuthenticated()
                  && current.getPrincipal() instanceof LoginUser user
                  && user.getUserId().equals(context.getVariables().get("userId"));
              return new AuthorizationDecision(owner);
            })
            .requestMatchers("/api/users/**").authenticated()
            .requestMatchers(HttpMethod.GET, "/api/teas", "/api/teas/**").permitAll()
            .requestMatchers(HttpMethod.HEAD, "/api/teas", "/api/teas/**").permitAll()
            // 包括批量新增及课堂 POST 预览；CSRF 校验继续生效。
            .requestMatchers(HttpMethod.POST, "/api/teas", "/api/teas/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/teas", "/api/teas/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/teas", "/api/teas/**").hasRole("ADMIN")
            .anyRequest().denyAll())
        .exceptionHandling(errors -> errors
            .authenticationEntryPoint((request, response, exception) ->
                json.error(response, 401, "UNAUTHENTICATED", "请先登录"))
            .accessDeniedHandler(denied))
        .requestCache(cache -> cache.requestCache(new NullRequestCache()))
        .sessionManagement(sessions -> sessions
            .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            .sessionFixation(fixation -> fixation.changeSessionId()))
        .formLogin(login -> login
            .loginPage("/api/auth/login")
            .loginProcessingUrl("/api/auth/login")
            .successHandler((request, response, authentication) ->
                json.write(response, 200, ((LoginUser) authentication.getPrincipal()).toResponse()))
            .failureHandler((request, response, exception) ->
                json.error(response, 401, "AUTHENTICATION_FAILED", "登录名或密码错误"))
            .permitAll())
        .logout(logout -> logout
            .logoutUrl("/api/auth/logout")
            .deleteCookies("JSESSIONID")
            .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
        .httpBasic(AbstractHttpConfigurer::disable)
        .csrf(Customizer.withDefaults());

    return http.build();
  }
}
