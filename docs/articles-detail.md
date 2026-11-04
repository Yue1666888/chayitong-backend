# 文章模块，第 3 步：公开详情与正文 JSON

本节增加 GET /api/articles/{id}，无需改表。
列表继续不查询正文；只有详情读取 content_blocks。
正文现已改为明确的 List<ContentBlock> 模型，建模教学见 content-block-models.md。

## 先分清数据库类型和 Java 类型

本项目实际建表使用的是 content_blocks JSON NOT NULL。
此前提到的 TEXT、MEDIUMTEXT 是普通长文本可选类型的举例，当前表和 Java 代码均未使用它们。

| 位置 | 名称或类型 |
|---|---|
| MySQL 字段 | content_blocks，类型 JSON |
| JDBC 读取 | resultSet.getString("content_blocks")，暂时得到 Java String |
| Java 解析 | ContentBlockReader.read(...)，得到具体内容块对象的列表 |
| 响应 DTO | ArticleDetailResponse.contentBlocks，类型 List<ContentBlock> |
| HTTP JSON | contentBlocks 数组 |

SQL 的 JSON 是数据库类型；Java 采用 ContentBlock 等业务类型，两者不需要同名。

## 核心 SQL

```sql
SELECT id, title, summary, category, cover, published_at, content_blocks
FROM articles
WHERE id = :id AND status = 'published';
```

编号使用命名参数绑定（本次文章搜索改造后使用 NamedParameterJdbcTemplate）。发布状态固定由后端限制，不接受客户端改变这个公开查询条件。
草稿与不存在的文章都返回同样的 404 / HTTP_404。
管理员访问这个公开详情接口也不会得到草稿；管理端预览草稿应另做受保护的接口。

## 正文如何成为数组

ArticleRepository 中的关键代码是：

```java
contentBlockReader.read(resultSet.getString("content_blocks"))
```

拆开理解，相当于：

```java
String jsonText = resultSet.getString("content_blocks");
List<ContentBlock> contentBlocks = contentBlockReader.read(jsonText);
```

ContentBlockReader 根据每个块的 type 读取 ParagraphBlock、ImageBlock 等明确的 Java 类。
读取器检查字段类型，随后调用 Validator 检查必填项与嵌套条目。
Spring 序列化这个 List 时输出 JSON 数组，保留已定义字段、内容块顺序、数字、布尔值和嵌套结构。

正确响应片段：

```json
{
  "contentBlocks": [
    {"type":"heading","level":3,"text":"认识文章内容块"},
    {"type":"paragraph","text":"这里是正文。"}
  ]
}
```

如果 DTO 直接用 String 保存 JSON 文本，默认序列化会把它当成普通字符串，
数组外面会有引号，内部引号会转义；使用 List 或 JsonNode 都可以输出真正的数组。
当前详情使用明确的 List<ContentBlock>；之前 JsonNode 的独立演示保留用于比较不同表示方式。
当前支持前端已有的 7 种块结构，遇到未知块类型、未知字段或不合法内容会报告数据格式错误。

## 分层

ArticleController.detail → ArticleService.findPublishedById → ArticleRepository.findPublishedById。
Repository 没有查到记录时返回 null；Controller 使用已有 ResponseStatusException 返回 404。
日期仍由 DATETIME 映射成 LocalDateTime，接口输出 publishedAt；北京时间约定沿用前一节。
已存正文不符合模型时，返回 500 / ARTICLE_CONTENT_INVALID；这表示服务端数据异常。
未来文章写入接口需要针对客户端输入返回参数错误，两类错误应区分。

## Apifox 验证

由你重新启动后端，统一使用 http://localhost:8080。
以下 GET 请求不需要登录、CSRF 请求头或 Body：
其中 article_storage 返回 404 的前提是它仍为草稿；完成 articles-publication.md 的发布练习后，该文章会改为返回 200。

1. GET /api/articles/article_six_types，应返回 200，contentBlocks 是长度为 2 的数组。
2. GET /api/articles/article_water_temperature，应返回 200 和对应正文。
3. GET /api/articles/article_storage，应返回 404 / HTTP_404，不能看到草稿标题或正文。
4. GET /api/articles/does_not_exist，应返回相同格式的 404。
5. 再 GET /api/articles，列表 items 中仍然没有 contentBlocks。

数据库字段 content_blocks 与响应属性 contentBlocks 的对应由 Java 读取和 DTO getter 明确完成。
这不是数据库自动改名，也不需要修改表字段。

## 自动验证

```powershell
mvn '-Ddb.integration=true' '-Dtest=ArticleDetailIntegrationTests,ArticleListIntegrationTests' test
```

测试使用完整安全过滤器链、真实数据库和事务内临时文章，结束后回滚。
覆盖正文数组、内容块顺序、数字/布尔值/嵌套列表、引号换行和反斜线、空数组、NULL 时间、
公开草稿保护、管理员身份下的草稿保护，以及列表继续只返回摘要。
