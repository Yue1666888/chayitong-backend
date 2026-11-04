package com.chayitong.chayitong_backend;

import com.chayitong.chayitong_backend.content.ContentBlock;
import com.chayitong.chayitong_backend.content.ContentBlockReader;
import com.chayitong.chayitong_backend.content.ParagraphBlock;
import jakarta.validation.Validation;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;

// 普通 main：观察 List<ContentBlock> 如何保存不同的具体类，不连接数据库。
public class ContentBlockModelDemo {
  public static void main(String[] args) {
    String jsonText = """
        [
          {"type":"paragraph","text":"你好，这是段落"},
          {"type":"image","src":"https://example.com/image.jpg","alt":"练习图片"},
          {"type":"list","ordered":false,"items":["第一项","第二项"]}
        ]
        """;
    try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
      ContentBlockReader reader = new ContentBlockReader(
          JsonMapper.builder().build(), validatorFactory.getValidator());
      List<ContentBlock> blocks = reader.read(jsonText);
      for (ContentBlock block : blocks) {
        System.out.println(block.getType() + " -> " + block.getClass().getSimpleName());
        if (block instanceof ParagraphBlock paragraph) {
          // 这里是明确的 Java 方法，拼错方法名会在编译时被发现。
          System.out.println("段落文字：" + paragraph.getText());
        }
      }
    }
  }
}
