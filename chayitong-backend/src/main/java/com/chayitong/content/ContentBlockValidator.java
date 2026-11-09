package com.chayitong.content;

import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.exceptions.BusinessException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class ContentBlockValidator {

    private static final Map<String, Set<String>> FIELDS = Map.of(
            "heading", Set.of("type", "text", "level"),
            "paragraph", Set.of("type", "text"),
            "image", Set.of("type", "src", "alt", "caption"),
            "quote", Set.of("type", "text", "source"),
            "list", Set.of("type", "items", "ordered"),
            "steps", Set.of("type", "items"),
            "tip", Set.of("type", "text", "tone", "title"));

    public List<JsonNode> validate(JsonNode blocks, boolean requireNonEmpty) {
        if (blocks == null || !blocks.isArray() || (requireNonEmpty && blocks.isEmpty())) {
            throw invalid("正文必须是内容块数组");
        }
        for (JsonNode block : blocks) {
            validateBlock(block);
        }
        return blocks.isEmpty() ? List.of() : java.util.stream.StreamSupport.stream(blocks.spliterator(), false).toList();
    }

    public List<JsonNode> validateStored(String json, JsonSupport jsonSupport) {
        return validate(jsonSupport.parse(json), false);
    }

    private void validateBlock(JsonNode block) {
        if (block == null || !block.isObject() || !block.path("type").isTextual()) {
            throw invalid("内容块类型不合法");
        }
        String type = block.path("type").asText();
        Set<String> allowed = FIELDS.get(type);
        if (allowed == null) {
            throw invalid("未知内容块类型");
        }
        block.properties().forEach(entry -> {
                    String field = entry.getKey();
            if (!allowed.contains(field)) {
                throw invalid("内容块包含未知字段: " + field);
            }
        });
        switch (type) {
            case "heading" -> {
                text(block, "text", true);
                if (!block.path("level").isInt() || Set.of(2, 3, 4).stream().noneMatch(level -> level == block.path("level").intValue())) {
                    throw invalid("标题层级仅支持 2、3、4");
                }
            }
            case "paragraph" -> text(block, "text", true);
            case "image" -> {
                validateImageUrl(text(block, "src", true));
                text(block, "alt", true);
                optionalText(block, "caption");
            }
            case "quote" -> {
                text(block, "text", true);
                optionalText(block, "source");
            }
            case "list" -> {
                stringItems(block.path("items"));
                if (!block.path("ordered").isBoolean()) {
                    throw invalid("列表 ordered 必须为布尔值");
                }
            }
            case "steps" -> {
                JsonNode items = block.path("items");
                if (!items.isArray() || items.isEmpty()) {
                    throw invalid("步骤不能为空");
                }
                for (JsonNode item : items) {
                    if (!item.isObject()) {
                        throw invalid("步骤结构不合法");
                    }
                    item.properties().forEach(entry -> {
                    String field = entry.getKey();
                        if (!Set.of("title", "text").contains(field)) {
                            throw invalid("步骤包含未知字段");
                        }
                    });
                    text(item, "title", true);
                    text(item, "text", true);
                }
            }
            case "tip" -> {
                text(block, "text", true);
                String tone = text(block, "tone", true);
                if (!Set.of("info", "warning", "success").contains(tone)) {
                    throw invalid("提示 tone 不合法");
                }
                optionalText(block, "title");
            }
            default -> throw invalid("未知内容块类型");
        }
    }

    private void stringItems(JsonNode items) {
        if (!items.isArray() || items.isEmpty()) {
            throw invalid("列表项不能为空");
        }
        for (JsonNode item : items) {
            if (!item.isTextual() || item.asText().isBlank()) {
                throw invalid("列表项必须是非空字符串");
            }
        }
    }

    private String text(JsonNode node, String field, boolean required) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || (required && value.asText().isBlank())) {
            throw invalid("内容块字段不合法: " + field);
        }
        return value.asText();
    }

    private void optionalText(JsonNode node, String field) {
        if (node.has(field)) {
            text(node, field, false);
        }
    }

    public void validateImageUrl(String value) {
        if (value.matches("^/api/media/[A-Za-z0-9_-]+/content$")) {
            return;
        }
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            boolean controlled = host != null && (host.equals("myqcloud.com") || host.endsWith(".myqcloud.com")
                    || host.endsWith(".qcloudcdn.cn") || host.endsWith(".tencentcos.cn"));
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !controlled || uri.getUserInfo() != null) {
                throw invalid("图片地址不受信任");
            }
        } catch (IllegalArgumentException exception) {
            throw invalid("图片地址不合法");
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.PARAMETER_ERROR, message);
    }
}
