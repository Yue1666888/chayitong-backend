package com.chayitong.chayitong_backend;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

// 独立运行 main：观察文本、JSON 树和字段校验，不启动后端或连接数据库。
public class JsonNodeAccessDemo {
  public static void main(String[] args) {
    ObjectMapper mapper = JsonMapper.builder().build();
    String jsonText = """
        [
          {"type":"paragraph","text":"你好"},
          {"type":"image","src":"https://example.com/photo.jpg"},
          {"type":"paragraph","text":123},
          {"type":"paragraph"}
        ]
        """.strip();
    JsonNode root = mapper.readTree(jsonText);

    System.out.println("1. 相同数据的两种表示：");
    System.out.println("文本的 Java 类型：" + jsonText.getClass().getSimpleName());
    System.out.println("文本第 0 个字符：" + jsonText.charAt(0));
    System.out.println("解析后根节点的 Java 类型：" + root.getClass().getSimpleName());
    System.out.println("根节点是否为数组：" + root.isArray());
    System.out.println("数组元素个数：" + root.size());

    // 先确认数组存在元素，再访问第 0 项。
    if (!root.isArray() || root.isEmpty()) {
      System.out.println("正文必须是非空数组");
      return;
    }
    JsonNode firstBlock = root.get(0);
    System.out.println("\n2. 访问解析后的数据：");
    System.out.println("第 0 项的 Java 类型：" + firstBlock.getClass().getSimpleName());
    JsonNode textNode = firstBlock.get("text");
    if (textNode != null && textNode.isString()) {
      System.out.println("第 0 项的 text：" + textNode.asString());
    }

    System.out.println("\n3. 不存在的字段：");
    JsonNode missingByGet = firstBlock.get("title");
    System.out.println("get(\"title\") 是否返回 Java null：" + (missingByGet == null));
    JsonNode missingByPath = firstBlock.path("title");
    System.out.println("path(\"title\") 是否为 MissingNode：" + missingByPath.isMissingNode());
    System.out.println("可选字段使用默认值：" + missingByPath.asString("未填写标题"));

    System.out.println("\n4. 按内容块类型校验必填字段：");
    int index = 0;
    for (JsonNode block : root) {
      String type = block.path("type").asString("");
      if ("paragraph".equals(type)) {
        JsonNode value = block.get("text");
        // 短路判断：缺失、JSON null、数字或空白文本，都不能作为有效段落正文。
        if (value == null || !value.isString() || value.asString().isBlank()) {
          System.out.println("第 " + index + " 项：段落 text 缺失、为空或类型不正确");
        } else {
          System.out.println("第 " + index + " 项：段落校验通过");
        }
      } else if ("image".equals(type)) {
        JsonNode src = block.get("src");
        boolean valid = src != null && src.isString() && !src.asString().isBlank();
        System.out.println("第 " + index + " 项：图片校验" + (valid ? "通过" : "失败"));
      } else {
        System.out.println("第 " + index + " 项：不支持的内容块类型");
      }
      index++;
    }
  }
}
