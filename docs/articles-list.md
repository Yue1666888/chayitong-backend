# 文章模块，第 2 步：公开分页列表

前提：已手动执行 articles 建表和演示数据脚本，并提交事务。
本页讲解公开列表；详情见 articles-detail.md，新增的分类/关键词组合查询见 articles-search.md。

## 接口

```http
GET /api/articles?page=1&pageSize=10
```

公开访问，无需登录、CSRF 请求头或 Body。
page 默认 1，pageSize 默认 10，最大 100。
响应使用 page、pageSize、total、items，items 每项只包含：

- id、title、summary、category、cover。
- publishedAt：数据库 published_at 映射后的日期时间。

列表不查询 content_blocks，也不返回整篇正文。
当前也支持可选 category、keyword；keyword 匹配标题或摘要，具体规则见 articles-search.md。
status 仍固定为 published，不能由客户端改变。

## 核心 SQL

```sql
SELECT id, title, summary, category, cover, published_at
FROM articles
WHERE status = 'published'
  AND (:category IS NULL OR category = :category)
  AND (:keywordPattern IS NULL OR (
    title LIKE :keywordPattern ESCAPE '!'
    OR summary LIKE :keywordPattern ESCAPE '!'
  ))
ORDER BY published_at DESC, id ASC
LIMIT :limit OFFSET :offset;
```

命名参数由 Java 绑定；未传筛选条件时绑定 NULL。COUNT 使用相同条件，total 只统计符合筛选的已发布文章。
同一发布时间用 id 排序，保证记录顺序稳定。
超出范围的页返回 200 和空 items，保留正确的 total。

## DATETIME 在哪里使用

表字段是 published_at DATETIME NULL，保存文章发布时间。
ArticleRepository 使用 resultSet.getObject("published_at", LocalDateTime.class) 接收它。
ArticleSummaryResponse 的 publishedAt 字段类型为 java.time.LocalDateTime。
接口返回示例："publishedAt": "2026-11-01T10:00:00"。
本节约定它表示北京时间，但字符串本身不包含时区或偏移量；LocalDateTime 本身也不包含时区。

## 分层

ArticleController → ArticleService → ArticleRepository → MySQL。

- Controller 接收并校验分页参数。
- Service 计算 offset，组织总数和当前页。
- Repository 执行只读 SQL，把每一行转换成 ArticleSummaryResponse。
- ArticlePageResponse 包装分页结果。

已有 TeaPageRequest 已改名为 PageRequest：茶叶、收藏、文章共用这份分页校验，不改 URL 参数名和规则。

## 手动验证

由你启动或重新启动后端，统一使用 http://localhost:8080。

1. GET /api/articles?page=1&pageSize=1，应返回较新的 article_water_temperature。
2. GET /api/articles?page=2&pageSize=1，应返回 article_six_types。
3. 使用此前的 3 篇演示文章且没有其他已发布记录时，total 应为 2。
4. 检查响应中没有 contentBlocks / content_blocks，草稿 article_storage 不在列表里。
5. 即使额外传 status=draft，仍只查询已发布文章。
6. page=99 应返回空 items，total 仍为 2（在只有原演示数据时）。
7. page=0 或 pageSize=101 应返回 400 / VALIDATION_FAILED。

## 自动验证

```powershell
mvn '-Ddb.integration=true' '-Dtest=ArticleListIntegrationTests,TeaPaginationIntegrationTests,TeaSearchIntegrationTests,SessionSecurityIntegrationTests,UserFavoritesIntegrationTests' test
```

文章测试启用完整过滤器链，验证访客访问、草稿隐藏、摘要字段、日期映射、稳定分页与参数校验。
临时文章随事务回滚，不修改你手工录入的文章。
其余测试验证共用 PageRequest 后的茶叶和收藏行为。
