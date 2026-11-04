package com.chayitong.chayitong_backend.content;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

// type 决定具体的 Java 类；读取和输出仍使用现有 JSON 中的 type 字段。
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ParagraphBlock.class, name = "paragraph"),
    @JsonSubTypes.Type(value = HeadingBlock.class, name = "heading"),
    @JsonSubTypes.Type(value = ImageBlock.class, name = "image"),
    @JsonSubTypes.Type(value = ListBlock.class, name = "list"),
    @JsonSubTypes.Type(value = QuoteBlock.class, name = "quote"),
    @JsonSubTypes.Type(value = StepsBlock.class, name = "steps"),
    @JsonSubTypes.Type(value = TipBlock.class, name = "tip")
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface ContentBlock {
  String getType();
}
