# 小练习：String、JsonNode、List 有什么区别

本节独立运行 JsonRepresentationDemo.main，不需要重启后端，不连接数据库。
在 VSCode 打开 src/test/java/com/chayitong/chayitong_backend/JsonRepresentationDemo.java，点击 main 上方的 Run。
这是普通演示程序，不是 JUnit 测试，不会自动随 mvn test 运行。

## JsonNode 是什么

JsonNode 是 Jackson 提供的 Java 类，用来表示 JSON 树中的一个值。
它本身也是 Java 对象。数组由 ArrayNode 表示，对象由 ObjectNode 表示，二者都可以用 JsonNode 类型的变量接收。
JsonNode 还可以表示字符串、数字、布尔值和 null 等 JSON 值。
当前文章正文的根节点是数组，所以 readTree 返回的实际对象是 ArrayNode。

## 可以不用 JsonNode 吗

可以。Java 的数组、List、Map、自定义 DTO 都可以参与 JSON 序列化。
接口里现有 ArticleDetailResponse 本身就是自定义 Java 对象。

当前文章的 contentBlocks 有多种结构：

```json
[
  {"type":"paragraph","text":"正文"},
  {"type":"image","src":"https://example.com/photo.jpg","alt":"图片说明"},
  {"type":"list","ordered":false,"items":["第一项","第二项"]}
]
```

此前的早期读取阶段选择 JsonNode，先不为每种块定义 Java 类型。
当前业务代码已改为 List<ContentBlock>，见 content-block-models.md；本演示继续用于比较表示方式。
也可以设计 List<ContentBlock> 等 DTO 模型，或使用 List<Map<String, Object>>；只是建模和访问方式不同。
固定结构用 DTO 往往更清晰；JsonNode 的字段名称和节点类型则主要由运行时读取判断。
当前选择 JsonNode 不表示其他 Java 数组或对象无法输出为 JSON。

## 为什么 String 版本会有引号和反斜线

下面这段 Java 变量保存的是文本，不是已经解析好的数组：

```java
String jsonText = """
    [{"type":"paragraph","text":"你好"}]
    """.strip();
```

序列化器看到 String，就按 JSON 字符串编码，不会擅自解析字符串里面的文字。
字符串内部原有的双引号需要转义，以免被误认为外层字符串已经结束。

String 字段输出：

```json
{"contentBlocks":"[{\"type\":\"paragraph\",\"text\":\"你好\"}]"}
```

JsonNode 或 List 字段输出：

```json
{"contentBlocks":[{"type":"paragraph","text":"你好"}]}
```

这两份都是合法 JSON，但第一份的字段值是字符串，第二份的字段值是数组。
转义本身是正确的字符串编码，不是 JSON 损坏或乱码。

客户端 response.json() 解析一次整个响应后，第一份的 data.contentBlocks 仍是字符串，第二份已经是数组。
因此第一份不能直接调用 data.contentBlocks.map(...)；本项目选择第二种接口结构。

## 数据流转

数据库 JSON → JDBC getString 得到文本 → readTree 解析为 JsonNode → Spring 序列化响应。

这里有两个方向：

- readTree：文本变为内存中的 JSON 结构，作用类似前端 JSON.parse。
- writeValueAsString：内存中的对象变为 JSON 文本，作用类似 JSON.stringify。

HTTP 响应最终仍是 JSON 文本；我们比较的是客户端解析整个响应后，contentBlocks 的值是什么类型。

## 演示程序的预期结果

- String 版本的 contentBlocks 是否为数组：false。
- JsonNode 版本：true。
- List 版本：true。
- 普通 String[] 可以输出为 ["绿茶","红茶"]。

Map 输出中属性的先后顺序可能不同，不影响对象字段含义；正文数组中元素的顺序则有意义。
