package com.chayitong.chayitong_backend;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Service
public class ObjectStorageService {
  private static final String IMAGE_PREFIX = "practice-images/";
  private static final String DOCUMENT_PREFIX = "practice-documents/";
  private static final String UUID_PATTERN = "[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}";
  private static final Pattern IMAGE_KEY =
      Pattern.compile(IMAGE_PREFIX + UUID_PATTERN + "\\.(png|jpg)");
  private static final Pattern DOCUMENT_KEY = Pattern.compile(DOCUMENT_PREFIX + UUID_PATTERN + "\\.pdf");
  private static final byte[] PDF_HEADER = "%PDF-".getBytes(StandardCharsets.US_ASCII);

  private final S3Client s3Client;
  private final S3Presigner presigner;
  private final TeaMapper teaMapper;
  private final String bucket;

  public ObjectStorageService(S3Client s3Client, S3Presigner presigner, TeaMapper teaMapper,
      @Value("${app.storage.bucket}") String bucket) {
    this.s3Client = s3Client;
    this.presigner = presigner;
    this.teaMapper = teaMapper;
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
    try (InputStream input = file.getInputStream()) {
      return putObject(key, contentType, input, file.getSize());
    } catch (IOException | SdkException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "上传到对象存储失败", exception);
    }
  }

  public byte[] downloadImage(String key) {
    return downloadObject(key, IMAGE_KEY);
  }

  public String uploadPdf(MultipartFile file) {
    if (file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择非空 PDF");
    }
    try (BufferedInputStream input = new BufferedInputStream(file.getInputStream())) {
      input.mark(PDF_HEADER.length);
      if (!Arrays.equals(input.readNBytes(PDF_HEADER.length), PDF_HEADER)) {
        throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "文件缺少 PDF 文件头");
      }
      // 读完文件头后回到开头，让对象存储收到完整文件。
      input.reset();
      String key = DOCUMENT_PREFIX + UUID.randomUUID() + ".pdf";
      return putObject(key, MediaType.APPLICATION_PDF_VALUE, input, file.getSize());
    } catch (IOException | SdkException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "上传到对象存储失败", exception);
    }
  }

  public byte[] downloadPdf(String key) {
    return downloadObject(key, DOCUMENT_KEY);
  }

  public String createTeaCoverUrl(String teaId) {
    String key = teaMapper.findCoverKey(teaId);
    if (key == null || !IMAGE_KEY.matcher(key).matches()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "茶叶封面不存在");
    }

    return presigner.presignGetObject(request -> request
        .signatureDuration(Duration.ofMinutes(5))
        .getObjectRequest(object -> object.bucket(bucket).key(key)))
        .url().toExternalForm();
  }

  private String putObject(String key, String contentType, InputStream input, long size) {
    PutObjectRequest request = PutObjectRequest.builder()
        .bucket(bucket).key(key).contentType(contentType).build();
    s3Client.putObject(request, RequestBody.fromInputStream(input, size));
    return key;
  }

  private byte[] downloadObject(String key, Pattern allowedKey) {
    if (!allowedKey.matcher(key).matches()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "对象 Key 格式不正确");
    }
    GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(key).build();
    try {
      return s3Client.getObjectAsBytes(request).asByteArray();
    } catch (NoSuchKeyException exception) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "对象不存在", exception);
    } catch (SdkException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "从对象存储读取失败", exception);
    }
  }
}
