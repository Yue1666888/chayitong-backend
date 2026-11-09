package com.chayitong.common.exceptions;

import com.chayitong.common.api.ErrorCode;

public class BusinessException extends RuntimeException {

	private final String code;
	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		this(errorCode, errorCode.getMessage());
	}

	public BusinessException(ErrorCode errorCode, String message) {
		super(message);
		this.code = errorCode.getCode();
		this.errorCode = errorCode;
	}

	public String getCode() {
		return code;
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}
}
