package com.chayitong.user;

import com.chayitong.auth.CurrentUser;
import com.chayitong.common.api.CommonResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/me")
public class UserController {

    private final UserProfileService userProfileService;
    private final UserService userService;

    public UserController(UserProfileService userProfileService, UserService userService) {
        this.userProfileService = userProfileService;
        this.userService = userService;
    }

    @GetMapping
    public CommonResponse<UserProfile> me() {
        return CommonResponse.success(userProfileService.profile(CurrentUser.require()));
    }

    @PatchMapping("/profile")
    public CommonResponse<UserProfile> update(@RequestBody JsonNode request) {
        return CommonResponse.success(userProfileService.update(CurrentUser.require(), request));
    }

    @GetMapping("/summary")
    public CommonResponse<UserSummary> summary() {
        return CommonResponse.success(userService.summary(CurrentUser.require()));
    }

    @PostMapping("/daily-points")
    public CommonResponse<DailyPointsResult> dailyPoints() {
        return CommonResponse.success(userService.claimDailyPoints(CurrentUser.require()));
    }
}
