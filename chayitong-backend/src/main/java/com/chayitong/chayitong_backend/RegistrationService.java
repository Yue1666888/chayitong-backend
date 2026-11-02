package com.chayitong.chayitong_backend;

import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
  private final UserRepository userRepository;
  private final UserCredentialRepository userCredentialRepository;
  private final PasswordEncoder passwordEncoder;

  public RegistrationService(UserRepository userRepository,
      UserCredentialRepository userCredentialRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.userCredentialRepository = userCredentialRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public UserResponse register(RegisterRequest request) {
    String userId = UUID.randomUUID().toString();
    String passwordHash = passwordEncoder.encode(request.getPassword());
    userRepository.insert(userId, request.getDisplayName());
    // 让重复登录名异常抛出业务层，事务会回滚上面新增的用户资料。
    userCredentialRepository.insert(userId, request.getLoginName(), passwordHash);
    return new UserResponse(userId, request.getDisplayName());
  }
}
