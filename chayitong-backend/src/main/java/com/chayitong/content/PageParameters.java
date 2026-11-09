package com.chayitong.content;

import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.exceptions.BusinessException;

public record PageParameters(int pageNum, int pageSize) {

    public static PageParameters of(Integer pageNum, Integer pageSize) {
        int page = pageNum == null ? 1 : pageNum;
        int size = pageSize == null ? 20 : pageSize;
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "分页参数不合法");
        }
        return new PageParameters(page, size);
    }

    public int offset() {
        return Math.multiplyExact(pageNum - 1, pageSize);
    }
}
