package com.chayitong.chayitong_backend;

import java.util.Map;

public class ApiErrorResponse {
  private final String code;
  private final String message;
  private final Map<String, String> fieldErrors;

  public ApiErrorResponse(String code, String message, Map<String, String> fieldErrors) {
    this.code = code;
    this.message = message;
    this.fieldErrors = fieldErrors;
  }

  public String getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }

  public Map<String, String> getFieldErrors() {
    return fieldErrors;
  }
}
