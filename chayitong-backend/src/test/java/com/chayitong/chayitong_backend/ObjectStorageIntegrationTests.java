package com.chayitong.chayitong_backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.s3.S3Client;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "storage.integration", matches = "true")
class ObjectStorageIntegrationTests {
  private static final String PATH = "/api/admin/storage/images";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private S3Client s3Client;

  @Value("${app.storage.bucket}")
  private String bucket;

  @Test
  void uploadsAndDownloadsTheSameBytesFromObjectStorage() throws Exception {
    byte[] png = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jPioAAAAASUVORK5CYII=");
    MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", png);
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
          .andExpect(content().bytes(png));
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
}
