package com.chayitong.content;

import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.exceptions.BusinessException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class JsonSupport {

    private final ObjectMapper objectMapper;

    public JsonSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "null" : json);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "存储的 JSON 内容无法读取");
        }
    }

    public List<JsonNode> array(String json) {
        JsonNode node = parse(json);
        if (!node.isArray()) {
            return List.of();
        }
        List<JsonNode> result = new ArrayList<>();
        node.forEach(result::add);
        return result;
    }

    public List<String> stringArray(String json) {
        List<String> result = new ArrayList<>();
        for (JsonNode node : array(json)) {
            if (node.isTextual()) {
                result.add(node.asText());
            }
        }
        return result;
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "JSON 内容无法保存");
        }
    }

    public ObjectMapper mapper() {
        return objectMapper;
    }
}
