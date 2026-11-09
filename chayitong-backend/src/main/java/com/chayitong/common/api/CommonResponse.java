package com.chayitong.common.api;

import java.time.Instant;
import org.slf4j.MDC;

public record CommonResponse<T>(
		String code,
		String message,
		T data,
		Instant timestamp,
		String traceId) {

	public static <T> CommonResponse<T> success(T data) {
		return new CommonResponse<>(BusinessCode.SUCCESS, "success", data, Instant.now(), currentTraceId());
	}

	public static CommonResponse<Void> success() {
		return success(null);
	}

	public static <T> CommonResponse<T> failure(String code, String message) {
		return new CommonResponse<>(code, message, null, Instant.now(), currentTraceId());
	}

	private static String currentTraceId() {
		return MDC.get(TraceId.HEADER_NAME);
	}
}
