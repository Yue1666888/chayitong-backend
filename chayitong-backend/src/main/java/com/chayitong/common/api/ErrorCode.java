package com.chayitong.common.api;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    PARAMETER_ERROR("VALIDATION_ERROR", "请求参数错误", HttpStatus.BAD_REQUEST),
    INVALID_FILE("INVALID_FILE", "文件内容或格式不合法", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("UNAUTHORIZED", "未认证或认证信息无效", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("FORBIDDEN", "没有访问权限", HttpStatus.FORBIDDEN),
    NOT_FOUND("NOT_FOUND", "资源不存在", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", "请求方法不支持", HttpStatus.METHOD_NOT_ALLOWED),
    STATE_CONFLICT("STATE_CONFLICT", "当前资源状态不允许此操作", HttpStatus.CONFLICT),
    IDEMPOTENCY_CONFLICT("IDEMPOTENCY_CONFLICT", "同一幂等键不能用于不同请求", HttpStatus.CONFLICT),
    INVENTORY_INSUFFICIENT("INVENTORY_INSUFFICIENT", "商品库存不足", HttpStatus.CONFLICT),
    QUOTE_EXPIRED("QUOTE_EXPIRED", "结算报价已过期", HttpStatus.CONFLICT),
    QUOTE_CHANGED("QUOTE_CHANGED", "商品价格或库存已变化", HttpStatus.CONFLICT),
    PAYLOAD_TOO_LARGE("PAYLOAD_TOO_LARGE", "上传内容超过大小限制", HttpStatus.PAYLOAD_TOO_LARGE),
    PAYMENT_UNAVAILABLE("PAYMENT_UNAVAILABLE", "支付能力当前不可用", HttpStatus.SERVICE_UNAVAILABLE),
    STORAGE_UNAVAILABLE("STORAGE_UNAVAILABLE", "文件存储服务当前不可用", HttpStatus.SERVICE_UNAVAILABLE),
    SYSTEM_ERROR("INTERNAL_ERROR", "系统内部错误", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;

    ErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
