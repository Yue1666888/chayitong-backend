package com.chayitong.user;

import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.exceptions.BusinessException;

public enum TargetType {
    TEA, ARTICLE, PRODUCT, POST;

    public static TargetType parse(String value) {
        if (value == null) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "目标类型不正确");
        }
        try {
            return valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "目标类型不正确");
        }
    }

    public String apiValue() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
