package com.chayitong.common.api;

import java.util.List;

public record PageResponse<T>(
		List<T> list,
		long total,
		long pageNum,
		long pageSize) {

	public static <T> PageResponse<T> of(List<T> list, long total, long pageNum, long pageSize) {
		return new PageResponse<>(list, total, pageNum, pageSize);
	}
}
