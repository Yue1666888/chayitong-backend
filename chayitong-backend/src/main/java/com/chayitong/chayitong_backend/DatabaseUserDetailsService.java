package com.chayitong.chayitong_backend;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
  private final UserCredentialRepository userCredentialRepository;

  public DatabaseUserDetailsService(UserCredentialRepository userCredentialRepository) {
    this.userCredentialRepository = userCredentialRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String username) {
    LoginUser user = userCredentialRepository.findByLoginName(username);
    // 本节使用带 {bcrypt} 前缀的编码；无账号和无效凭据都按认证失败处理。
    if (user == null || !user.getPassword().startsWith("{bcrypt}")) {
      throw new UsernameNotFoundException("登录名或密码错误");
    }
    return user;
  }
}
