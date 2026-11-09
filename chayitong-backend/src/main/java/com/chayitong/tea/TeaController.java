package com.chayitong.tea;

import com.chayitong.common.api.CommonResponse;
import com.chayitong.common.api.PageResponse;
import com.chayitong.content.PageParameters;
import com.chayitong.tea.TeaModels.TeaDetail;
import com.chayitong.tea.TeaModels.TeaRequest;
import com.chayitong.tea.TeaModels.TeaSummary;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TeaController {

    private final TeaService teaService;

    public TeaController(TeaService teaService) {
        this.teaService = teaService;
    }

    @GetMapping("/api/teas")
    public CommonResponse<PageResponse<TeaSummary>> list(@RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) Boolean featured,
            @RequestParam(required = false) Integer pageNum, @RequestParam(required = false) Integer pageSize) {
        return CommonResponse.success(teaService.listPublic(type, keyword, featured, PageParameters.of(pageNum, pageSize)));
    }

    @GetMapping("/api/teas/{id}")
    public CommonResponse<TeaDetail> detail(@PathVariable String id) {
        return CommonResponse.success(teaService.getPublic(id));
    }

    @GetMapping("/api/admin/teas")
    public CommonResponse<PageResponse<TeaSummary>> adminList(@RequestParam(required = false) String type,
            @RequestParam(required = false) String status, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer pageNum, @RequestParam(required = false) Integer pageSize) {
        return CommonResponse.success(teaService.listAdmin(type, status, keyword, PageParameters.of(pageNum, pageSize)));
    }

    @PostMapping("/api/admin/teas")
    public CommonResponse<TeaDetail> create(@RequestBody TeaRequest request) {
        return CommonResponse.success(teaService.create(request));
    }

    @GetMapping("/api/admin/teas/{id}")
    public CommonResponse<TeaDetail> adminDetail(@PathVariable String id) {
        return CommonResponse.success(teaService.getAdmin(id));
    }

    @PutMapping("/api/admin/teas/{id}")
    public CommonResponse<TeaDetail> update(@PathVariable String id, @RequestBody TeaRequest request) {
        return CommonResponse.success(teaService.update(id, request));
    }

    @DeleteMapping("/api/admin/teas/{id}")
    public CommonResponse<Void> delete(@PathVariable String id) {
        teaService.delete(id);
        return CommonResponse.success();
    }
}
