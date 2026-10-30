package com.chayitong.chayitong_backend;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
public class HealthController {

  @GetMapping("/api/health")
  public String healthCheck() {
    return "Chayitong backend is running!";
  }

  @GetMapping("/api/hello")
  public String hello(@RequestParam("username") String username) {
    return "Hello, " + username + "！欢迎来到茶一统。";
  }

}
