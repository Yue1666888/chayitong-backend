package com.chayitong.common.exceptions;

import com.chayitong.common.api.CommonResponse;
import com.chayitong.common.api.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<CommonResponse<Void>> handleBusinessException(BusinessException exception) {
        return response(exception.getErrorCode(), exception.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<CommonResponse<Void>> handleValidationException(Exception exception) {
        String message = ErrorCode.PARAMETER_ERROR.getMessage();
        if (exception instanceof MethodArgumentNotValidException validationException) {
            var fieldError = validationException.getBindingResult().getFieldError();
            if (fieldError != null) {
                message = fieldError.getDefaultMessage();
            }
        } else if (exception instanceof BindException bindException && bindException.getFieldError() != null) {
            message = bindException.getFieldError().getDefaultMessage();
        }
        return response(ErrorCode.PARAMETER_ERROR, message);
    }

    @ExceptionHandler({ConstraintViolationException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<CommonResponse<Void>> handleParameterException(Exception exception) {
        return response(ErrorCode.PARAMETER_ERROR, ErrorCode.PARAMETER_ERROR.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<CommonResponse<Void>> handleUploadSize(MaxUploadSizeExceededException exception) {
        return response(ErrorCode.PAYLOAD_TOO_LARGE, ErrorCode.PAYLOAD_TOO_LARGE.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<CommonResponse<Void>> handleNotFoundException(NoResourceFoundException exception) {
        return response(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.getMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<CommonResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        return response(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponse<Void>> handleException(Exception exception) {
        log.error("Unhandled exception", exception);
        return response(ErrorCode.SYSTEM_ERROR, ErrorCode.SYSTEM_ERROR.getMessage());
    }

    private ResponseEntity<CommonResponse<Void>> response(ErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(CommonResponse.failure(errorCode.getCode(), message));
    }
}
