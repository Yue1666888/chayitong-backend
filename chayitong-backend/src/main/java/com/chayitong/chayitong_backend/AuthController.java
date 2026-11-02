package com.chayitong.chayitong_backend;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
  private final RegistrationService registrationService;

  public AuthController(RegistrationService registrationService) {
    this.registrationService = registrationService;
  }

  @PostMapping("/api/auth/register")
  public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(registrationService.register(request));
  }

  @GetMapping("/api/auth/csrf")
  public CsrfTokenResponse csrf(CsrfToken token) {
    return new CsrfTokenResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
  }

  @GetMapping("/api/auth/me")
  public UserResponse me(@AuthenticationPrincipal LoginUser user) {
    return user.toResponse();
  }
}
