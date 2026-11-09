package com.chayitong.auth;

import com.chayitong.common.api.CommonResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/wechat/login")
    public CommonResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return CommonResponse.success(authService.login(request.code()));
    }

    @PostMapping("/refresh")
    public CommonResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return CommonResponse.success(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public CommonResponse<Void> logout() {
        authService.logout(CurrentUser.require());
        return CommonResponse.success();
    }

    public record LoginRequest(@NotBlank String code) {
    }

    public record RefreshRequest(String refreshToken) {
    }
}
