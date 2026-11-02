package com.chayitong.chayitong_backend;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class ExampleController {

  private final TeaService teaService;

  // Spring 创建控制器时，通过构造方法传入 TeaService 对象。
  public ExampleController(TeaService teaService) {
    this.teaService = teaService;
  }

  @GetMapping("/api/teas/preview")
  public TeaResponse teasPreview(
      @RequestParam("id") String id,
      @RequestParam("name") String name,
      @RequestParam("type") String type) {
    return new TeaResponse(id, name, type);
  }

  @PostMapping(value = "/api/teas/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
  public TeaResponse previewTea(@Valid @RequestBody TeaPreviewRequest request) {
    // 只生成预览响应，尚未保存到数据库，因此没有持久化编号。
    return new TeaResponse(null, request.getName(), request.getType());
  }

  @GetMapping("/api/teas")
  public TeaPageResponse listTeas(
      @Valid @ModelAttribute TeaFilterRequest filter,
      @Valid @ModelAttribute PageRequest pagination) {
    return teaService.listTeas(filter.getType(), filter.getKeyword(),
        pagination.getPage(), pagination.getPageSize());
  }

  @PostMapping(value = "/api/teas", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<TeaResponse> createTea(@Valid @RequestBody TeaCreateRequest request) {
    TeaResponse tea = teaService.createTea(
        request.getId(), request.getName(), request.getType());

    // 创建成功返回 201，Location 告诉客户端新资源的查询地址。
    URI location = URI.create("/api/teas/" + tea.getId());
    return ResponseEntity.created(location).body(tea);
  }

  @PostMapping(value = "/api/teas/batch", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<TeaResponse>> createTeas(
      @Valid @RequestBody TeaBatchCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(teaService.createTeas(request.getItems()));
  }

  @GetMapping("/api/teas/{id}")
  public TeaResponse getTea(@PathVariable("id") String id) {
    TeaResponse tea = teaService.findById(id);
    if (tea != null) {
      return tea;
    }

    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "茶叶不存在");
  }

  @PutMapping(value = "/api/teas/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
  public TeaResponse updateTea(
      @PathVariable("id") String id,
      @Valid @RequestBody TeaUpdateRequest request) {
    TeaResponse tea = teaService.updateTea(id, request.getName(), request.getType());
    if (tea == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "茶叶不存在");
    }
    return tea;
  }

  @DeleteMapping("/api/teas/{id}")
  public ResponseEntity<Void> deleteTea(@PathVariable("id") String id) {
    if (!teaService.deleteTea(id)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "茶叶不存在");
    }
    // 204 表示操作成功，不返回响应正文。
    return ResponseEntity.noContent().build();
  }

}
