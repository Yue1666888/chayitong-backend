# 文章正文建模：从 JsonNode 到 List<ContentBlock>

本节把文章详情的正文改为明确的 Java 业务模型。
数据库继续使用 content_blocks JSON，已有有效数据和接口 JSON 结构沿用原格式。

## 先只看三个位置

1. content/ContentBlock.java：正文块的共同接口，以及 type 与具体类的对应表。
2. content/ParagraphBlock.java：最简单的正文块，声明 String text、getter、setter。
3. ArticleDetailResponse.java：字段改成 List<ContentBlock>。

本节先理解共同接口、implements 和列表中的实际对象。
读取器中严格类型转换的配置可以后看，不要求记住 Jackson 所有配置选项。

## 共同接口

```java
public interface ContentBlock {
  String getType();
}
```

ParagraphBlock implements ContentBlock，表示它提供接口要求的 getType() 方法。
ImageBlock、ListBlock 等也实现同一个接口，因此可以放进 List<ContentBlock>。
与之前 List 变量可以引用不同实现类的对象一样，这里共同类型可以引用不同内容块对象。

## type 怎么决定 Java 类

ContentBlock 上的 @JsonTypeInfo 配置使用 JSON 中已有的 type 字段作为类型标识。
@JsonSubTypes 列出逻辑名称与 Java 类的对应：

| JSON type | Java 类 | 主要字段 |
|---|---|---|
| paragraph | ParagraphBlock | String text |
| heading | HeadingBlock | Integer level、String text |
| image | ImageBlock | String src、alt、caption |
| list | ListBlock | Boolean ordered、List<String> items |
| quote | QuoteBlock | String text、source |
| steps | StepsBlock | List<StepItem> items |
| tip | TipBlock | String tone、title、text |

StepItem 是步骤条目，具有 String title、text。
每个具体类的 getType() 返回固定的逻辑名称。
EXISTING_PROPERTY 表示输出时沿用 getType() 产生的 type 属性，不再额外输出另一份类型标识。
实际负责选择类的是 Jackson 的读取配置，不是 List 本身。

## 类型确定之后怎么访问

```java
for (ContentBlock block : blocks) {
  System.out.println(block.getType());
  if (block instanceof ParagraphBlock paragraph) {
    System.out.println(paragraph.getText());
  }
}
```

共同接口只承诺 getType()，所以只有确认是 ParagraphBlock 后，才访问它的 getText()。
instanceof 同时检查具体类型并把该对象交给 paragraph 变量。
方法名拼错会在编译时被发现，不再通过 get("text") 的字符串取业务字段。

## 运行时仍需检查外部数据

ContentBlockReader 使用专用于文章正文的读取器：

1. 根据 type 读取允许的具体类，不接受未知 type 或未知字段。
2. 拒绝把数字 123 静默转成段落字符串 "123"，也不把字符串 "false" 当作布尔值。
3. 调用 Validator，检查 @NotBlank、@NotNull、标题级别 1～6、列表条目和嵌套步骤等规则。
4. 数组内不允许 null；正文空数组 [] 仍沿用原先读取行为。

这些配置只作用于文章正文，不修改其他接口使用的 ObjectMapper。
图片的 alt/caption、引用的 source、提示的 tone/title 可以缺省；@JsonInclude(NON_NULL) 会省略缺省或显式为 null 的可选字段输出。
图片 src 的校验目前只要求非空字符串，不表示已经验证图片可访问。

数据库只保证正文是 JSON 数组；如果已有记录中有未知块或不合法字段，公开详情会返回
500 / ARTICLE_CONTENT_INVALID，避免悄悄转换或丢弃字段。
这是读取服务端数据时的错误；未来写接口应对不合规则的客户端输入返回 400。
列表不读取正文，仍可以正常返回摘要。

## 自己运行一个小例子

打开 src/test/java/com/chayitong/chayitong_backend/ContentBlockModelDemo.java，点击 main 上方的 Run。
无需启动后端或连接数据库。

```text
paragraph -> ParagraphBlock
段落文字：你好，这是段落
image -> ImageBlock
list -> ListBlock
```

## Apifox 验证

由你重启后端，再 GET /api/articles/article_six_types。
contentBlocks 仍是数组，原有 heading、paragraph 等字段保持原结构。
GET /api/articles/article_storage 仍应返回 404。
本次不需要改表或重新录入现有演示文章。

## 自动验证

```powershell
mvn '-Ddb.integration=true' '-Dtest=ArticleDetailIntegrationTests,ArticleListIntegrationTests' test
```

覆盖全部 7 种内容块的字段和值、具体 Java 类型、嵌套列表、缺省可选字段、
未知类型/字段、缺失必填值、数字到字符串等不允许的转换及草稿保护。
测试生成的临时文章和正文修改均随事务回滚，不修改你的已有文章。
