package com.chayitong.shop;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.exceptions.BusinessException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
final class ShopSupport {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ObjectMapper objectMapper;

    ShopSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    long id() {
        return IdWorker.getId();
    }

    String businessKey(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    String transactionNo(String prefix) {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        return prefix + HexFormat.of().formatHex(bytes).toUpperCase();
    }

    String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Unable to serialize shop data", exception);
        }
    }

    List<String> stringList(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid stored shop JSON", exception);
        }
    }

    List<JsonNode> nodeList(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid stored shop JSON", exception);
        }
    }

    JsonNode node(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid stored shop JSON", exception);
        }
    }

    static BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.PARAMETER_ERROR, message);
    }

    static BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND);
    }

    static BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.STATE_CONFLICT, message);
    }

    static String text(String value) {
        return value == null ? null : value.trim();
    }
}
