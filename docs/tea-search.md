# 茶名搜索，第 1 步：LIKE

前端已有茶叶搜索入口。本节只改后端，在 GET /api/teas 增加 keyword 参数。
目前仅匹配 teas.name，不搜索编号、茶类、香气或产地，不进行分词。
不需要改表，也不自动插入业务演示数据。

## 接口行为

```http
GET /api/teas?keyword=龙井&type=绿茶&page=1&pageSize=5
```

- keyword 可选，最多 50 个字符；去掉首尾空白后为空时，不启用关键词过滤。
- keyword 的含义是“茶名包含完整关键词”。词中间的空格保留。
- type 可选，继续精确筛选茶类；空白 type 不启用茶类过滤。
- keyword 与 type 同时提供时，两者必须同时满足。
- page、pageSize 和响应结构继续沿用原分页接口，按 id 排序。
- total 是符合全部条件的总数；items 是其中的当前页。
- 没有匹配时返回 200、total=0、items=[]。
- GET 接口公开访问，不需要登录、CSRF 请求头或 Body。

## 先在 DBCode 练习查询

以下全是 SELECT，不修改数据：

```sql
SELECT id, name, type
FROM teas
WHERE name LIKE '%龙井%'
ORDER BY id;

SELECT id, name, type
FROM teas
WHERE name LIKE '%龙井%'
  AND type = '绿茶'
ORDER BY id
LIMIT 5 OFFSET 0;
```

LIKE 用于模式匹配，% 表示任意数量的字符（包括零个）：

| 条件 | 含义 |
|---|---|
| name = '龙井' | 名字与“龙井”相等 |
| name LIKE '龙井%' | 以“龙井”开头 |
| name LIKE '%龙井' | 以“龙井”结尾 |
| name LIKE '%龙井%' | 名字包含“龙井” |

因此西湖龙井可被最后一条 LIKE 条件匹配，却不会被 name = '龙井' 匹配。
本节只需要先掌握 LIKE 和 %，下面的转义与可选条件可以对着代码逐步理解。

## Java 中的调用顺序

1. ExampleController 将 URL 中的 type、keyword 绑定给 TeaFilterRequest；分页绑定到通用 PageRequest（原名 TeaPageRequest）。
2. @Valid 校验关键词长度和分页参数。
3. TeaService 去除关键词首尾空白；没有关键词时继续使用原来的查询路径。
4. 有关键词时，TeaRepository.countByKeyword 查询总数，findByKeyword 查询当前页。
5. 两个 SQL 使用相同 WHERE 条件，避免 total 与 items 对不上。

关键词查询条件为：

```sql
WHERE name LIKE ? ESCAPE '!'
  AND (? IS NULL OR type = ?)
```

第一个 ? 绑定包含匹配模式，例如 %龙井%；后两个 ? 都绑定同一个 type 值。
type 为 NULL 时，IS NULL 成立，不限制茶类；否则必须满足 type = ?。
分页查询最后两个 ? 再依次绑定 limit 和 offset。

## 为什么还有 ESCAPE 和 replace

SQL 的 LIKE 中 %、_ 是通配符。本接口把用户输入当作普通搜索文本：
用户输入 % 时，应搜索名字里的百分号，而不是直接匹配全部茶叶。

使用 ESCAPE '!' 后，!% 表示普通百分号，!_ 表示普通下划线，!! 表示普通感叹号。
containsPattern 先处理用户输入中的 !、%、_，再在两边加上用于“包含”的 %。
参数绑定的 ? 防止输入变成 SQL 语句；LIKE 转义则控制搜索的匹配含义，两者作用不同。

## Apifox 手动验证

由你重新启动后端。使用 http://localhost:8080，Params 中填写中文参数即可。

1. GET /api/teas?keyword=龙井，应能查到现有的西湖龙井。
2. 加上 type=绿茶，返回茶名匹配且茶类为绿茶的结果。
3. 改成 type=红茶，是否有结果取决于数据，不能只凭关键词断定数量。
4. GET /api/teas?keyword=毛&page=1&pageSize=1，观察 total 和 items 数量的区别；可切到第 2 页。
5. keyword 留空或全是空格，应恢复原来的列表/茶类筛选行为。
6. keyword 填写 51 个字符，应返回 400 / VALIDATION_FAILED，fieldErrors 包含 keyword。
7. 搜索一个不存在的茶名，应得到 200 和空列表，而不是 404。

若直接在 URL 输入 %，需要编码为 %25；建议在 Apifox Params 面板输入原文，让客户端编码。

## 自动验证

```powershell
mvn '-Ddb.integration=true' '-Dtest=TeaSearchIntegrationTests,TeaPaginationIntegrationTests' test
```

搜索测试使用完整安全过滤器链，验证访客访问；临时茶叶在测试事务内回滚。
原分页测试覆盖旧参数兼容性。两类测试均不启动 HTTP 服务器。
