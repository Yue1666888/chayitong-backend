package com.chayitong.chayitong_backend;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

// 安全过滤器在 MVC 之外运行，需要自己写入 JSON 响应。
@Component
public class SecurityJsonResponse {
  private final ObjectMapper objectMapper;

  public SecurityJsonResponse(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public void write(HttpServletResponse response, int status, Object body) throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    objectMapper.writeValue(response.getOutputStream(), body);
  }

  public void error(HttpServletResponse response, int status, String code, String message)
      throws IOException {
    write(response, status, new ApiErrorResponse(code, message, Map.of()));
  }
}
