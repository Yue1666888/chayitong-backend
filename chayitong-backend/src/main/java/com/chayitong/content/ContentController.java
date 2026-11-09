package com.chayitong.content;

import com.chayitong.common.api.CommonResponse;
import com.chayitong.content.ContentModels.AppConfig;
import com.chayitong.content.ContentModels.Home;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class ContentController {

    private final ContentConfigService contentConfigService;

    public ContentController(ContentConfigService contentConfigService) {
        this.contentConfigService = contentConfigService;
    }

    @GetMapping("/api/home")
    public CommonResponse<Home> home() {
        return CommonResponse.success(contentConfigService.home());
    }

    @GetMapping("/api/app-config")
    public CommonResponse<AppConfig> config() {
        return CommonResponse.success(contentConfigService.publicConfig());
    }

    @PutMapping("/api/admin/app-config")
    public CommonResponse<AppConfig> updateConfig(@RequestBody JsonNode request) {
        return CommonResponse.success(contentConfigService.update(request));
    }
}
