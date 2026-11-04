# 文章模块：管理员发布文章

本节实现一个管理操作：将已有草稿发布。
无需改表或新增字段。实际练习文章由你在 Apifox 发布，自动测试只使用临时数据。

## 接口与结果

```http
PUT /api/admin/articles/{id}/publication
```

要求 ADMIN 身份、会话 Cookie 和有效 CSRF 请求头，无需 Body。
成功返回 200 和 id、status、publishedAt；日期按本项目约定表示北京时间。

| 情况 | 结果 |
|---|---|
| 有效草稿 | 更新成 published，记录本次发布时间 |
| 已发布的有效文章 | 返回当前发布状态，保留原有发布时间 |
| 文章不存在 | 404 / HTTP_404 |
| 标题/分类为空白、正文为空或不符合内容块模型 | 409 / ARTICLE_NOT_READY，状态不变 |
| 普通用户，CSRF 有效 | 403 / FORBIDDEN |
| 未登录，CSRF 有效 | 401 / UNAUTHENTICATED |
| CSRF 缺失或错误 | 403 / CSRF_FAILED，可能在身份/角色判断之前被拦截 |

发布和撤回共用 publication 路径，通过 PUT 和 DELETE 区分。

## 数据与业务顺序

AdminArticleController → ArticleService.publish → ArticleRepository。

Service 的 @Transactional 将以下步骤放入同一笔事务：

1. 按 id 查询目标文章并锁定记录，管理业务在这里可以检查草稿。
2. 通过 ContentBlockReader 检查正文是否符合已定义的内容块模型。
3. 检查标题、分类非空白，正文至少有一个块。
4. 仅当当前状态为 draft 时，更新状态和发布时间。
5. 查询实际发布状态，事务提交后返回响应。

不存在的编号由 Controller 返回 404。校验不通过则抛出 ArticleNotReadyException，事务回滚。
普通公开详情继续使用带 status='published' 的查询，不会提前暴露草稿。

## 为什么重复发布不改变时间

```sql
UPDATE articles
SET status = 'published', published_at = :publishedAt
WHERE id = :id AND status = 'draft';
```

第一次将 draft 改成 published。
再次请求时，status='draft' 不再成立，因此不会重写 published_at。
接口再查询当前状态并返回 200，目标仍然是“这篇文章已经发布”。
如果一个 draft 已带有旧的发布时间，本次由草稿发布时会记录新的时间。
对于已经 published 的历史记录，本操作保留原有字段值，不承担数据修复功能。

## FOR UPDATE 先知道用途即可

```sql
SELECT id, title, summary, category, cover, published_at, content_blocks
FROM articles
WHERE id = :id
FOR UPDATE;
```

这是锁定读取，需要在事务里使用。
它锁住目标记录，其他事务要修改或以冲突方式锁定同一记录时需要等待；普通快照查询通常仍可读取已提交版本。
锁在事务提交或回滚时释放，避免“刚校验完，文章又被另一个写请求修改”的间隙。
本节先记住用途，后续再单独学习锁和并发。

## 为什么正文不合法时是 409

公开 GET 读取坏的服务端正文时，仍返回原来的 500 / ARTICLE_CONTENT_INVALID。
发布动作会把这一情况解释为“当前文章还不满足发布条件”，返回 409 / ARTICLE_NOT_READY。
这里没有新增客户端正文写入接口，发布请求只携带文章编号。

## Apifox 手动步骤

由你重新启动后端，统一使用 http://localhost:8080，Apifox 自动保存和发送 Cookie。

1. GET /api/articles/article_storage：仍为 draft 时应返回 404。
2. 如果当前登录的是普通账号，先带当前有效 CSRF 正常退出。
3. GET /api/auth/csrf，取得登录前值。
4. POST /api/auth/login，Body 选择 x-www-form-urlencoded：username=xiaolin，password 使用该账号原密码。
   携带第 3 步 CSRF。登录成功后，再 GET /api/auth/csrf 取得登录后的值。
5. PUT /api/admin/articles/article_storage/publication，无 Body，携带 Cookie 和登录后取得的 CSRF 请求头。
6. 应返回 200、status=published 和实际 publishedAt。记下时间，再重复发送，时间应保持相同。
7. GET /api/articles/article_storage：现在应返回 200 和正文。
8. GET /api/articles?category=storage：原练习草稿现在应出现在公开列表中。

同一有效会话中，发布本身不重置 CSRF，重复发布可以复用当前有效值。
你发布了原来的储存文章后，早期练习中的“总计 2 篇已发布”“storage 分类为空”等预期会随数据变化。

可在 DBCode 通过只读查询观察变化：

```sql
SELECT id, title, status, published_at
FROM articles
WHERE id = 'article_storage';
```

不需要在 DBCode 手动 UPDATE；这次由后端接口执行状态修改。

## 撤回发布

```http
DELETE /api/admin/articles/{id}/publication
```

用管理员 Cookie 和有效 CSRF 请求头发送，Body 留空。撤回会把状态设为 draft，并清空 published_at。这个操作保留文章记录和正文。

用 article_storage 练习时，先用 PUT 发布，再对同一地址发送 DELETE。成功返回 204。公开详情会返回 404，分类列表也会隐藏该文章。重复撤回仍返回 204，编号不存在时返回 404。再用 PUT 发布会重新记录发布时间。

撤回直接更新状态和时间，因此即使已存正文格式异常，也可以先撤回公开展示。

## 自动验证

```powershell
mvn '-Ddb.integration=true' '-Dtest=ArticlePublicationIntegrationTests,ArticleDetailIntegrationTests,ArticleListIntegrationTests,ArticleSearchIntegrationTests' test
```

发布测试不在外层包裹测试事务，以验证业务的真实提交、失败回滚和多连接并发。
测试用随机编号创建临时账号、文章，结束后只删除这些临时记录，不修改已有账号或文章。
测试覆盖实际登录、角色与 CSRF、发布可见性、历史时间保留、空/错误正文、未知编号和并发发布。
