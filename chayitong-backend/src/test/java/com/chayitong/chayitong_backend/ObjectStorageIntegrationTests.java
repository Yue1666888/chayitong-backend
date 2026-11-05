package com.chayitong.chayitong_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "storage.integration", matches = "true")
class ObjectStorageIntegrationTests {
  private static final String PATH = "/api/admin/storage/images";
  private static final String DOCUMENT_PATH = "/api/admin/storage/documents";
  private static final byte[] PNG = Base64.getDecoder().decode(
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jPioAAAAASUVORK5CYII=");

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private S3Client s3Client;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private ObjectStorageService storage;

  @Value("${app.storage.bucket}")
  private String bucket;

  @Test
  void uploadsAndDownloadsTheSameBytesFromObjectStorage() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", PNG);
    String body = mockMvc.perform(multipart(PATH).file(file)
            .with(user("admin").roles("ADMIN")).with(csrf()))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    String key = JsonPath.read(body, "$.key");

    try {
      mockMvc.perform(get(PATH).param("key", key).with(user("admin").roles("ADMIN")))
          .andExpect(status().isOk())
          .andExpect(content().contentType("image/png"))
          .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
              "attachment; filename=\"" + key.substring("practice-images/".length()) + "\""))
          .andExpect(content().bytes(PNG));
      mockMvc.perform(get(PATH).param("key", key).with(user("reader").roles("USER")))
          .andExpect(status().isForbidden());
    } finally {
      s3Client.deleteObject(request -> request.bucket(bucket).key(key));
    }
  }

  @Test
  void returnsNotFoundForAnAbsentObject() throws Exception {
    mockMvc.perform(get(PATH).param("key", "practice-images/" + UUID.randomUUID() + ".png")
            .with(user("admin").roles("ADMIN")))
        .andExpect(status().isNotFound());
  }

  @Test
  void preservesThePdfHeaderAndDownloadsTheOriginalBytes() throws Exception {
    byte[] bytes = "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    MockMultipartFile file = new MockMultipartFile(
        "file", "practice.pdf", MediaType.APPLICATION_PDF_VALUE, bytes);
    String body = mockMvc.perform(multipart(DOCUMENT_PATH).file(file)
            .with(user("admin").roles("ADMIN")).with(csrf()))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    String key = JsonPath.read(body, "$.key");
    try {
      assertThat(key).startsWith("practice-documents/").endsWith(".pdf");
      mockMvc.perform(get(DOCUMENT_PATH).param("key", key).with(user("admin").roles("ADMIN")))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_PDF))
          .andExpect(content().bytes(bytes));
    } finally {
      s3Client.deleteObject(request -> request.bucket(bucket).key(key));
    }
  }

  @Test
  void rejectsAFileWithThePdfNameAndMediaTypeButNoPdfHeader() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "file", "fake.pdf", MediaType.APPLICATION_PDF_VALUE, PNG);
    mockMvc.perform(multipart(DOCUMENT_PATH).file(file)
            .with(user("admin").roles("ADMIN")).with(csrf()))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  @Transactional
  @EnabledIfSystemProperty(named = "db.integration", matches = "true")
  void signsTheCoverStoredOnTheTeaRecordForAnAdministrator() throws Exception {
    String id = "it_" + UUID.randomUUID().toString().substring(0, 16);
    String path = "/api/admin/storage/tea-covers/{teaId}";
    String key = storage.uploadImage(new MockMultipartFile("file", "cover.png", "image/png", PNG));
    try {
      mockMvc.perform(get(path, id + "-missing").with(user("admin").roles("ADMIN")))
          .andExpect(status().isNotFound());
      jdbcTemplate.update("INSERT INTO teas (id, name, type, cover_key) VALUES (?, ?, ?, ?)",
          id, "测试茶", "绿茶", key);
      mockMvc.perform(get(path, id).with(user("reader").roles("USER")))
          .andExpect(status().isForbidden());
      String body = mockMvc.perform(get(path, id).with(user("admin").roles("ADMIN")))
          .andExpect(status().isOk())
          .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
          .andReturn().getResponse().getContentAsString();

      String url = JsonPath.read(body, "$.url");
      HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).build();
      try (HttpClient client = HttpClient.newHttpClient()) {
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(PNG);
      }
    } finally {
      s3Client.deleteObject(request -> request.bucket(bucket).key(key));
    }
  }
}
