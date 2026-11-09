package com.chayitong.user;

import com.chayitong.auth.CurrentUser;
import com.chayitong.common.api.CommonResponse;
import com.chayitong.common.api.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/me/history")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public CommonResponse<PageResponse<HistoryItem>> list(@RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") @Min(1) long pageNum,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize) {
        return CommonResponse.success(historyService.list(CurrentUser.require(),
                type == null ? null : TargetType.parse(type), pageNum, pageSize));
    }

    @PutMapping("/{type}/{id}")
    public CommonResponse<HistoryItem> put(@PathVariable String type, @PathVariable String id) {
        return CommonResponse.success(historyService.put(CurrentUser.require(), TargetType.parse(type), id));
    }

    @DeleteMapping("/{type}/{id}")
    public CommonResponse<Void> delete(@PathVariable String type, @PathVariable String id) {
        historyService.delete(CurrentUser.require(), TargetType.parse(type), id);
        return CommonResponse.success();
    }

    @DeleteMapping
    public CommonResponse<Void> clear() {
        historyService.clear(CurrentUser.require());
        return CommonResponse.success();
    }
}
