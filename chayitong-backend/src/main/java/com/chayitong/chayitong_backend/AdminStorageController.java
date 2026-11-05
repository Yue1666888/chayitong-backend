package com.chayitong.chayitong_backend;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/storage/images")
public class AdminStorageController {
  private final ObjectStorageService storage;

  public AdminStorageController(ObjectStorageService storage) {
    this.storage = storage;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public UploadResponse upload(@RequestPart("file") MultipartFile file) {
    return new UploadResponse(storage.uploadImage(file));
  }

  @GetMapping
  public ResponseEntity<byte[]> download(@RequestParam("key") String key) {
    byte[] bytes = storage.downloadImage(key);
    String filename = key.substring(key.lastIndexOf('/') + 1);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).toString())
        .body(bytes);
  }

  public record UploadResponse(String key) {}
}
