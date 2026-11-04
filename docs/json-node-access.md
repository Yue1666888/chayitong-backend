# 小练习：JSON 文本、内存结构和缺失字段

打开 src/test/java/com/chayitong/chayitong_backend/JsonNodeAccessDemo.java，运行 main。
这是独立演示，不启动 Spring，不连接数据库，不改变已有接口。

## 文本和结构是同一份数据的不同表示

JSON 文本是一串符合 JSON 语法的字符，Java 使用 String 保存它。
解析后的结构由 Java 对象组成，这次 Jackson 使用 ArrayNode、ObjectNode 等 JsonNode 子类表示。

```java
String jsonText = """
    [{"type":"paragraph","text":"你好"}]
    """;
JsonNode root = mapper.readTree(jsonText);
```

解析前，jsonText.charAt(0) 得到字符 '['；String 并没有数组第 0 项或 text 字段的概念。
解析后，root.get(0) 得到第一个对象节点，再 get("text") 得到字段值节点。
String.length() 是字符串长度；数组节点的 size() 则是数组元素个数。
节点打印时又被转换成 JSON 文本，所以两者打印外观可能相似，内存中的类型和操作方式仍然不同。

可以用前端类比：JSON 字符串通过 JSON.parse 变成数组或对象；这里 readTree 生成的是 Jackson 的 Java 对象。

## 字段不存在的风险是真实的

JsonNode 不要求你预先定义与这份数据逐字段对应的业务 DTO，但仍在使用 Jackson 提供的 Java 类。
编译器无法检查 get("字段名") 里的字符串是否拼对，也不会自动检查业务必填字段。

```java
firstBlock.get("title").asString();
```

样例中 title 不存在，get 会返回 Java null；继续调用 asString 会产生空指针异常。
所以对不确定的数据，不应直接假设多层 get 的结果都存在。

## 必填字段：检查存在、类型和内容

```java
JsonNode textNode = block.get("text");
if (textNode == null || !textNode.isString() || textNode.asString().isBlank()) {
  // 必填字段不符合规则，实际写接口时应拒绝这个请求。
} else {
  String text = textNode.asString();
}
```

依次检查：字段是否缺失、是不是字符串、是否全为空白。
Java 的 || 会短路：左侧已经成立时，右侧不再执行，因此不会对 null 调用方法。
字段显式写成 JSON null 时，get 返回 NullNode；isString 也会返回 false，这段检查同样可以拒绝它。
JSON 格式合法，只说明可以解析，不说明业务字段合法。

## 可选字段：可以提供默认值

```java
String title = firstBlock.path("title").asString("未填写标题");
```

path 在没有匹配字段时返回 MissingNode，而不是 Java null，适合继续链式访问。
asString("默认值") 是本项目 Jackson 3 的默认值读取方法。
默认值不等于业务校验：必填字段不能因为读取时有默认值就直接视为合法。

| 数据情况 | get("text") 的结果 |
|---|---|
| 没有 text 字段 | Java null |
| "text": null | NullNode |
| "text": "你好" | 字符串值节点 |
| "text": 123 | 数字值节点 |

## 为什么需要按 type 分别校验

图片块没有 text 不一定有错，它应检查 src；段落块则应检查 text。
演示中的 4 个块依次为：合法段落、合法图片、text 为数字的段落、缺少 text 的段落。
程序应让前两个通过，识别后两个不符合段落规则。

## 对当前接口的影响

在此前 JsonNode 读取阶段，ArticleRepository 只解析并返回整个正文树，没有在后端逐个读取块内的 text 或 src。
当前业务代码已改用明确的 List<ContentBlock> 模型和运行时校验，见 content-block-models.md。
本页和 JsonNodeAccessDemo 保留为独立的 JSON 树访问练习，不表示当前文章详情仍采用 JsonNode 字段。
数据库本身仍只检查 JSON 语法和顶层数组；每种内容块的字段由 Java ContentBlockReader 校验。
后续管理员文章写接口也需要在保存前校验内容块。
如果改为 DTO，也仍需 @Valid、@NotBlank 等规则；仅仅定义一个 Java 类并不能保证外部数据合法。
