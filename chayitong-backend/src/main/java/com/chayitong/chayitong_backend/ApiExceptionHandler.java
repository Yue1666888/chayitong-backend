package com.chayitong.chayitong_backend;

import com.chayitong.chayitong_backend.content.InvalidArticleContentException;
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

  @ExceptionHandler(PublishedArticleDeleteException.class)
  public ResponseEntity<ApiErrorResponse> handlePublishedArticleDelete(PublishedArticleDeleteException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ApiErrorResponse("ARTICLE_DELETE_REQUIRES_DRAFT", exception.getMessage(), Map.of()));
  }

  @ExceptionHandler(PublishedArticleEditException.class)
  public ResponseEntity<ApiErrorResponse> handlePublishedArticleEdit(PublishedArticleEditException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ApiErrorResponse("ARTICLE_EDIT_REQUIRES_DRAFT", exception.getMessage(), Map.of()));
  }

  @ExceptionHandler(InvalidArticleDraftContentException.class)
  public ResponseEntity<ApiErrorResponse> handleInvalidDraftContent(InvalidArticleDraftContentException exception) {
    return ResponseEntity.badRequest().body(new ApiErrorResponse(
        "VALIDATION_FAILED", "请求参数校验失败", Map.of("contentBlocks", exception.getMessage())));
  }

  @ExceptionHandler(ArticleNotReadyException.class)
  public ResponseEntity<ApiErrorResponse> handleArticleNotReady(ArticleNotReadyException exception) {
    ApiErrorResponse body = new ApiErrorResponse(
        "ARTICLE_NOT_READY", exception.getMessage(), new HashMap<>());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
  }

  @ExceptionHandler(InvalidArticleContentException.class)
  public ResponseEntity<ApiErrorResponse> handleInvalidArticleContent(InvalidArticleContentException exception) {
    ApiErrorResponse body = new ApiErrorResponse(
        "ARTICLE_CONTENT_INVALID", "文章正文格式异常，请联系管理员", new HashMap<>());
    // 出错的是服务端已有数据，不是本次 GET 请求参数，因此返回 500。
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }

  @ExceptionHandler(DuplicateKeyException.class)
  public ResponseEntity<ApiErrorResponse> handleDuplicateKey(DuplicateKeyException exception) {
    ApiErrorResponse body = new ApiErrorResponse(
        "DATA_CONFLICT", "数据与现有记录冲突，请检查编号或登录名是否重复", new HashMap<>());
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
