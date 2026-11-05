package com.chayitong.chayitong_backend;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class ObjectStorageService {
  private static final String IMAGE_PREFIX = "practice-images/";
  private static final Pattern IMAGE_KEY =
      Pattern.compile(IMAGE_PREFIX + "[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.(png|jpg)");

  private final S3Client s3Client;
  private final String bucket;

  public ObjectStorageService(S3Client s3Client, @Value("${app.storage.bucket}") String bucket) {
    this.s3Client = s3Client;
    this.bucket = bucket;
  }

  public String uploadImage(MultipartFile file) {
    if (file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择非空图片");
    }
    String contentType = file.getContentType();
    String extension = switch (contentType == null ? "" : contentType) {
      case "image/png" -> ".png";
      case "image/jpeg" -> ".jpg";
      default -> throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "请选择 PNG 或 JPEG");
    };
    String key = IMAGE_PREFIX + UUID.randomUUID() + extension;
    PutObjectRequest request = PutObjectRequest.builder()
        .bucket(bucket).key(key).contentType(contentType).build();
    try (InputStream input = file.getInputStream()) {
      s3Client.putObject(request, RequestBody.fromInputStream(input, file.getSize()));
      return key;
    } catch (IOException | SdkException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "上传到对象存储失败", exception);
    }
  }

  public byte[] downloadImage(String key) {
    if (!IMAGE_KEY.matcher(key).matches()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片 Key 格式不正确");
    }
    GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(key).build();
    try {
      return s3Client.getObjectAsBytes(request).asByteArray();
    } catch (NoSuchKeyException exception) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在", exception);
    } catch (SdkException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "从对象存储读取失败", exception);
    }
  }
}
