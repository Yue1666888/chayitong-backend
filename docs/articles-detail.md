# 文章模块，第 3 步：公开详情与正文 JSON

本节增加 GET /api/articles/{id}，无需改表。
列表继续不查询正文；只有详情读取 content_blocks。

## 先分清数据库类型和 Java 类型

本项目实际建表使用的是 content_blocks JSON NOT NULL。
此前提到的 TEXT、MEDIUMTEXT 是普通长文本可选类型的举例，当前表和 Java 代码均未使用它们。

| 位置 | 名称或类型 |
|---|---|
| MySQL 字段 | content_blocks，类型 JSON |
| JDBC 读取 | resultSet.getString("content_blocks")，暂时得到 Java String |
| Java 解析 | objectMapper.readTree(...)，得到 JsonNode |
| 响应 DTO | ArticleDetailResponse.contentBlocks，类型 JsonNode |
| HTTP JSON | contentBlocks 数组 |

SQL 的 JSON 是数据库类型；JsonNode 是 Jackson 提供的 Java 类型，两者不需要同名。

## 核心 SQL

```sql
SELECT id, title, summary, category, cover, published_at, content_blocks
FROM articles
WHERE id = ? AND status = 'published';
```

编号使用参数绑定。发布状态固定由后端限制，不接受客户端改变这个公开查询条件。
草稿与不存在的文章都返回同样的 404 / HTTP_404。
管理员访问这个公开详情接口也不会得到草稿；管理端预览草稿应另做受保护的接口。

## 正文如何成为数组

ArticleRepository 中的关键代码是：

```java
objectMapper.readTree(resultSet.getString("content_blocks"))
```

拆开理解，相当于：

```java
String jsonText = resultSet.getString("content_blocks");
JsonNode contentBlocks = objectMapper.readTree(jsonText);
```

readTree 将 JSON 文本解析为内存中的 JSON 结构，用途类似前端 JSON.parse。
JsonNode 可以表示数组、对象、字符串、数字等；本表已有 CHECK 约束要求正文顶层是数组。
Spring 序列化响应时会把它输出为真正的 JSON 数组，保留内容块顺序、数字、布尔值和嵌套结构。

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
数组外面会有引号，内部引号会转义；所以这次详情 DTO 使用 JsonNode。
目前不重新加工块内容，前端已有的段落、标题、列表、图片等结构可以保持原样。

## 分层

ArticleController.detail → ArticleService.findPublishedById → ArticleRepository.findPublishedById。
Repository 没有查到记录时返回 null；Controller 使用已有 ResponseStatusException 返回 404。
日期仍由 DATETIME 映射成 LocalDateTime，接口输出 publishedAt；北京时间约定沿用前一节。

## Apifox 验证

由你重新启动后端，统一使用 http://localhost:8080。
以下 GET 请求不需要登录、CSRF 请求头或 Body：

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
