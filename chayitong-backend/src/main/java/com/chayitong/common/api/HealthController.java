package com.chayitong.common.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public CommonResponse<Health> health() {
        return CommonResponse.success(new Health("UP"));
    }

    public record Health(String status) {
    }
}
