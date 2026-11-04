package com.chayitong.chayitong_backend;

import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

// 独立运行 main，比较 JSON 文本、JsonNode 和 Java 集合；不启动 Spring 或连接数据库。
public class JsonRepresentationDemo {
  public static void main(String[] args) {
    ObjectMapper mapper = JsonMapper.builder().build();

    // 这只是 String，内容的文字恰好符合 JSON 格式。
    String jsonText = """
        [{"type":"paragraph","text":"你好"}]
        """.strip();

    // 解析后得到真正的 JSON 树；这份数据的根节点是 ArrayNode。
    JsonNode node = mapper.readTree(jsonText);

    // 也可以直接使用 Java 的 List 和 Map 表示同样的数据。
    List<Map<String, String>> blocks = List.of(
        Map.of("type", "paragraph", "text", "你好"));

    // 模拟响应序列化：只有 contentBlocks 的值类型不同。
    String stringResponse = mapper.writeValueAsString(Map.of("contentBlocks", jsonText));
    String nodeResponse = mapper.writeValueAsString(Map.of("contentBlocks", node));
    String listResponse = mapper.writeValueAsString(Map.of("contentBlocks", blocks));

    System.out.println("1. String 原文：");
    System.out.println(jsonText);

    System.out.println("\n2. 把 String 放进响应，contentBlocks 是 JSON 字符串：");
    System.out.println(stringResponse);

    System.out.println("\n3. 把 JsonNode 放进响应，contentBlocks 是 JSON 数组：");
    System.out.println(nodeResponse);
    System.out.println("node 实际对象类型：" + node.getClass().getSimpleName());

    System.out.println("\n4. 把 List 放进响应，contentBlocks 同样是 JSON 数组：");
    System.out.println(listResponse);

    // 模拟客户端解析一次整个响应，再检查 contentBlocks 是否为数组。
    System.out.println("\n5. 客户端解析响应一次后，contentBlocks 是否为数组：");
    System.out.println("String 版本：" + mapper.readTree(stringResponse).get("contentBlocks").isArray());
    System.out.println("JsonNode 版本：" + mapper.readTree(nodeResponse).get("contentBlocks").isArray());
    System.out.println("List 版本：" + mapper.readTree(listResponse).get("contentBlocks").isArray());

    // 普通 Java 数组也能正常序列化为 JSON 数组。
    String[] teaNames = {"绿茶", "红茶"};
    System.out.println("\n6. Java String[] 数组也可以返回：");
    System.out.println(mapper.writeValueAsString(Map.of("names", teaNames)));
  }
}
