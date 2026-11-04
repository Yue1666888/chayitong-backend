# 文章模块：分类与关键词组合查询

本节为 GET /api/articles 增加 category、keyword，继续使用公开访问和分页。
无需改表，不修改前端。

## 参数

```http
GET /api/articles?category=brewing&keyword=水温&page=1&pageSize=5
```

| 参数 | 含义 |
|---|---|
| category | 分类值相等匹配，最多 32 个字符；不是标题中展示的分类中文名称 |
| keyword | 标题或摘要包含关键词，最多 50 个字符 |
| page / pageSize | 沿用已有分页规则 |

category 和 keyword 均可省略；去除首尾空白后为空，就不启用对应过滤条件。
当前只搜索 title、summary，不搜索正文、标签或分类名称；前端现有搜索覆盖范围更广，后续逐步补齐。
关键词中的 %、_、! 按普通字符搜索；由 SqlLikePattern 生成包含模式，茶叶搜索也复用该方法。
总数与当前页使用同一组条件，total 始终统计符合全部条件的已发布文章。
无结果返回 200、total=0、items=[]；页码超出范围返回空 items，但保留实际 total。

## 本节重点：AND 与 OR 的括号

用具体值写出的查询是：

```sql
SELECT id, title, summary, category, published_at
FROM articles
WHERE status = 'published'
  AND category = 'brewing'
  AND (title LIKE '%水温%' OR summary LIKE '%水温%')
ORDER BY published_at DESC, id ASC;
```

含义：必须已发布，必须属于 brewing，同时标题或摘要至少一个命中。

如果写成：

```sql
WHERE status = 'published'
  AND category = 'brewing'
  AND title LIKE '%水温%'
  OR summary LIKE '%水温%'
```

由于 AND 优先于 OR，实际会按“前面三个条件同时成立，或者摘要命中”处理。
这样摘要命中的草稿或其他分类文章也可能被查出。
因此需要把属于同一组搜索条件的 OR 放进括号，再与发布状态、分类条件用 AND 连接。

## 为什么代码里出现 :category

ArticleRepository 本节使用 NamedParameterJdbcTemplate。
它将命名参数转为底层 JDBC 参数绑定，参数值仍不会拼接成 SQL 代码。
MapSqlParameterSource 用于提供名称与值的对应关系，例如：

```java
new MapSqlParameterSource()
    .addValue("category", "brewing")
    .addValue("keywordPattern", "%水温%");
```

SQL 中同一个 :keywordPattern 可以多次出现，使用同一个值。
因为 category、keyword 都是可选参数，实际代码使用：

```sql
WHERE status = 'published'
  AND (:category IS NULL OR category = :category)
  AND (:keywordPattern IS NULL OR (
    title LIKE :keywordPattern ESCAPE '!'
    OR summary LIKE :keywordPattern ESCAPE '!'
  ))
```

参数为 NULL 时，不启用相应过滤；参数有值时，要求分类匹配或标题/摘要命中。
COUNT 与列表复用 PUBLISHED_FILTER，列表额外绑定 limit、offset。
代码中的 SQL 字符串拼接只组合我们写死的 SQL 片段，请求参数仍通过名称绑定，没有拼入 SQL 结构。
详情查询也改用 :id 绑定，发布状态和正文模型保持原规则。

## 自己在 DBCode 练习

sql/08-article-search.sql 只有 SELECT，不修改数据。
其中的布尔表达式示例不依赖你是否恰好有摘要命中的草稿：

```sql
SELECT
    FALSE AND FALSE OR TRUE AS without_group,
    FALSE AND (FALSE OR TRUE) AS with_group;
```

结果分别为 1、0。括号决定了条件如何组成一个整体。

## Apifox 验证

由你重新启动后端，统一使用 http://localhost:8080；GET 无需登录、CSRF 或 Body。
在原有三篇练习文章未改变时，可以这样检查：

1. category=brewing：返回 article_water_temperature。
2. keyword=水温：通过标题命中 article_water_temperature。
3. keyword=第一篇练习：通过摘要命中 article_six_types。
4. category=brewing、keyword=水温、pageSize=1：total 为 1，items 为 1 条。
5. category=storage：只有草稿，不应返回文章。
6. category=storage、keyword=草稿：仍然不应返回草稿。
7. page=2、pageSize=1，在组合结果仅 1 条时，应返回空 items，total 仍为 1。
8. keyword 留空或全为空白时，继续使用分类/普通列表查询。

category 超过 32 字符或 keyword 超过 50 字符，返回 400 / VALIDATION_FAILED。
额外传入 status=draft 不会改变公开查询的状态限制。

## 自动验证

```powershell
mvn '-Ddb.integration=true' '-Dtest=ArticleSearchIntegrationTests,ArticleListIntegrationTests,ArticleDetailIntegrationTests,TeaSearchIntegrationTests' test
```

测试验证标题/摘要分别命中、组合分类、草稿保护、分页总数、首尾空白、原文特殊字符、
SQL 形式的输入、参数校验，以及列表/详情/茶叶搜索的兼容性。
临时文章随测试事务回滚，不修改现有数据。
