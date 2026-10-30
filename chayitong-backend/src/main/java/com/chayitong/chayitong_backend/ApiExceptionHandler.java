package com.chayitong.chayitong_backend;

import java.util.HashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(DuplicateKeyException.class)
  public ResponseEntity<ApiErrorResponse> handleDuplicateKey(DuplicateKeyException exception) {
    ApiErrorResponse body = new ApiErrorResponse(
        "DATA_CONFLICT", "数据与现有记录冲突，请检查编号是否重复", new HashMap<>());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiErrorResponse> handleDataConstraint(DataIntegrityViolationException exception) {
    ApiErrorResponse body = new ApiErrorResponse(
        "DATA_CONSTRAINT_VIOLATION", "数据不符合数据库约束，请刷新后重试", new HashMap<>());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    Map<String, String> fieldErrors = new HashMap<>();
    for (FieldError error : exception.getBindingResult().getFieldErrors()) {
      // 一个字段可能违反多条规则，这里保留其中一条提示。
      String message = error.isBindingFailure() ? "参数格式不正确" : error.getDefaultMessage();
      fieldErrors.putIfAbsent(error.getField(), message);
    }

    ApiErrorResponse body = new ApiErrorResponse(
        "VALIDATION_FAILED", "请求参数校验失败", fieldErrors);
    return handleExceptionInternal(exception, body, headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ApiErrorResponse body = new ApiErrorResponse(
        "INVALID_JSON", "请求体必须是格式正确的 JSON 对象", new HashMap<>());
    return handleExceptionInternal(exception, body, headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> createResponseEntity(
      Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    // 其他 Spring MVC 请求错误也使用相同格式，同时保留原来的状态码和响应头。
    if (!(body instanceof ApiErrorResponse)) {
      String message = "请求处理失败";
      if (status.value() == 400) {
        message = "请求参数不符合接口要求";
      } else if (status.value() == 404) {
        message = "请求的资源不存在";
      } else if (status.value() == 405) {
        message = "接口不支持此请求方法";
      } else if (status.value() == 415) {
        message = "接口不支持此请求体格式";
      }
      body = new ApiErrorResponse("HTTP_" + status.value(), message, new HashMap<>());
    }

    return new ResponseEntity<>(body, headers, status);
  }
}
