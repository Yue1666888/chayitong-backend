package com.chayitong.chayitong_backend.content;

import jakarta.validation.Validator;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;

@Component
public class ContentBlockReader {
  private final ObjectReader reader;
  private final Validator validator;

  public ContentBlockReader(ObjectMapper objectMapper, Validator validator) {
    this.validator = validator;
    // 仅为文章正文建立严格读取器，不改变其他接口使用的全局 ObjectMapper。
    ObjectMapper contentMapper = objectMapper.rebuild()
        .withCoercionConfig(String.class, config -> config
            .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
            .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
            .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
        .withCoercionConfig(Integer.class, config -> config
            .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
            .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
            .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
        .withCoercionConfig(Boolean.class, config -> config
            .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
            .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
            .setCoercion(CoercionInputShape.Float, CoercionAction.Fail))
        .build();
    this.reader = contentMapper.readerForListOf(ContentBlock.class)
        .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .without(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY,
            DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS);
  }

  public List<ContentBlock> read(String jsonText) {
    if (jsonText == null) {
      throw new InvalidArticleContentException();
    }
    try {
      return validateBlocks(reader.readValue(jsonText));
    } catch (JacksonException exception) {
      throw new InvalidArticleContentException(exception);
    }
  }

  public List<ContentBlock> read(JsonNode node) {
    if (node == null || !node.isArray()) {
      throw new InvalidArticleContentException();
    }
    try {
      return validateBlocks(reader.readValue(node));
    } catch (JacksonException exception) {
      throw new InvalidArticleContentException(exception);
    }
  }

  private List<ContentBlock> validateBlocks(List<ContentBlock> blocks) {
    if (blocks == null) {
      throw new InvalidArticleContentException();
    }
    for (ContentBlock block : blocks) {
      // 字段注解只有实际调用校验时才会生效，同时拒绝数组内的 null。
      if (block == null || !validator.validate(block).isEmpty()) {
        throw new InvalidArticleContentException();
      }
    }
    return blocks;
  }
}
