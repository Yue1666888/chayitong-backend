package com.chayitong.chayitong_backend;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/storage")
public class AdminStorageController {
  private final ObjectStorageService storage;

  public AdminStorageController(ObjectStorageService storage) {
    this.storage = storage;
  }

  @PostMapping(path = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public UploadResponse upload(@RequestPart("file") MultipartFile file) {
    return new UploadResponse(storage.uploadImage(file));
  }

  @GetMapping("/images")
  public ResponseEntity<byte[]> download(@RequestParam("key") String key) {
    byte[] bytes = storage.downloadImage(key);
    String filename = key.substring(key.lastIndexOf('/') + 1);
    // 下载服务只接受 .png 和 .jpg 格式的练习 Key。
    MediaType contentType = filename.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
    return attachment(bytes, filename, contentType);
  }

  @PostMapping(path = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public UploadResponse uploadDocument(@RequestPart("file") MultipartFile file) {
    return new UploadResponse(storage.uploadPdf(file));
  }

  @GetMapping("/documents")
  public ResponseEntity<byte[]> downloadDocument(@RequestParam("key") String key) {
    byte[] bytes = storage.downloadPdf(key);
    String filename = key.substring(key.lastIndexOf('/') + 1);
    return attachment(bytes, filename, MediaType.APPLICATION_PDF);
  }

  @GetMapping("/tea-covers/{teaId}")
  public ResponseEntity<CoverLinkResponse> getTeaCoverLink(@PathVariable("teaId") String teaId) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(new CoverLinkResponse(storage.createTeaCoverUrl(teaId)));
  }

  private ResponseEntity<byte[]> attachment(byte[] bytes, String filename, MediaType contentType) {
    ContentDisposition disposition = ContentDisposition.attachment().filename(filename).build();
    return ResponseEntity.ok()
        .contentType(contentType)
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        .body(bytes);
  }

  public record UploadResponse(String key) {}
  public record CoverLinkResponse(String url) {}
}
