package com.chayitong.chayitong_backend;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

// 供安全框架内部使用；接口只返回 toResponse()，不返回密码哈希。
public class LoginUser implements UserDetails, CredentialsContainer {
  private static final long serialVersionUID = 1L;
  private final String userId;
  private final String displayName;
  private final String loginName;
  private final String role;
  private String passwordHash;

  public LoginUser(String userId, String displayName, String loginName, String passwordHash, String role) {
    this.userId = userId;
    this.displayName = displayName;
    this.loginName = loginName;
    this.passwordHash = passwordHash;
    this.role = role;
  }

  public String getUserId() {
    return userId;
  }

  public String getRole() {
    return role;
  }

  @Override
  public String getUsername() {
    return loginName;
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    // 数据库保存 USER / ADMIN，框架用 ROLE_USER / ROLE_ADMIN 表示角色权限。
    return List.of(new SimpleGrantedAuthority("ROLE_" + role));
  }

  @Override
  public void eraseCredentials() {
    passwordHash = null;
  }

  public UserResponse toResponse() {
    return new UserResponse(userId, displayName);
  }
}
