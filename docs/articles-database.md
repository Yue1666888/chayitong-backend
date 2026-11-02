# 文章模块，第 1 步：手动准备数据库

本节只准备文章表和练习数据，由你在 DBCode 执行。
Java 接口在下一步实现；本节不修改前端，也不需要启动或重启后端。

## 为什么现在加文章

前端 LocalRepository 已有 listArticles / getArticle，文章详情页用 contentBlocks 数组渲染正文。
我们先让后端保存同类结构，再逐步写文章列表和详情接口。
标题、摘要、分类等适合单独成列；正文内容块使用 JSON 数组，保留原来的有序结构。
本节先准备基础字段，标签、关联茶叶、推荐和阅读统计后续逐步加入。

## 本节字段

| 字段 | 类型 | 用途 |
|---|---|---|
| id | VARCHAR(64) | 文章编号，主键 |
| title | VARCHAR(100) | 标题 |
| summary | VARCHAR(500) | 列表摘要，默认空字符串 |
| category | VARCHAR(32) | 分类，例如 tea_basics、brewing、storage |
| cover | VARCHAR(1024)，允许 NULL | 封面图片地址；NULL 表示没有提供封面 |
| content_blocks | JSON | 正文内容块数组 |
| status | VARCHAR(16) | draft 草稿或 published 已发布，默认 draft |
| published_at | DATETIME，允许 NULL | 发布时间；草稿示例尚无发布时间 |

默认 draft 意味着新增文章未指定状态时，不会因默认值而直接公开。
status 与之前的用户角色一样，用 CHECK 约束限定可用值。
公开可见性由后续接口执行 WHERE status = 'published' 实现，数据库不会自动隐藏草稿。

## 新知识 1：JSON 字段

这与前端常见的 JSON 是同一种数据格式。例如：

```json
[
  {"type":"heading","level":3,"text":"认识茶叶"},
  {"type":"paragraph","text":"这里是正文。"}
]
```

这整个数组保存在一行文章的 content_blocks 字段中，数组元素顺序也被保留。
MySQL 的 JSON 类型会校验 JSON 格式；额外的 JSON_TYPE(...)= 'ARRAY' 约束要求顶层是数组。
它不会自动检查每个块是否有 type、text 等字段，这些内容规则由后续 Java 代码处理。

SQL 的字符串外层用单引号，内部 JSON 属性名和字符串使用双引号：

```sql
'[{"type":"paragraph","text":"这里是正文。"}]'
```

不要把 JSON 的双引号全部改成单引号。

### 单个字段能保存大量正文吗

可以，正文保存在一个字段中是常见做法，容量取决于数据类型与配置。
普通文本常用 TEXT（最多 65,535 字节）或 MEDIUMTEXT（最多 16,777,215 字节）；字节数不等于中文字数。
本项目正文是内容块数组，所以选择 JSON，既保留文字，也保留段落、标题等块结构。
JSON 文档大小受 max_allowed_packet 等限制；本次只读检查得到该配置为 67,108,864 字节，即 64 MiB。
这不是业务上鼓励的大正文大小；后续写入接口仍需制定合理的正文长度限制。
图片本身继续由图片地址引用，正文 JSON 保存地址及说明，不把图片二进制塞进这个字段。
列表接口只查询标题、摘要等字段，读取单篇详情时再查询正文。

## 新知识 2：DATETIME 和 NULL

DATETIME 保存日期和时间，例如 '2026-11-01 09:00:00'。
它本身不保存时区信息，本节明确约定这些值表示北京时间，后续接口映射时继续保持这个约定。
NULL 表示尚未提供这个值；字符串 'NULL' 只是四个字母，不表示数据库的空值。
没有发布时间的草稿使用 NULL，不使用空字符串或零日期。

## 1. 选择连接与数据库

使用你之前连接 Docker MySQL 的 DBCode 连接，选择 chayitong。
结束之前未完成的事务练习后，执行：

```sql
SELECT DATABASE();
SHOW TABLES LIKE 'articles';
```

本次只读检查发现 articles 尚不存在。
如果你执行时已经存在，先 SHOW CREATE TABLE articles，跳过重复建表，不要删除已有表。

## 2. 建表

打开 sql/06-articles.sql，选中 CREATE TABLE 语句执行一次。
然后执行 DESCRIBE articles，应看到上面 8 个字段。
CREATE TABLE 会隐式提交，不能靠普通 ROLLBACK 撤销建表。

## 3. 插入 3 篇练习文章

打开 sql/07-articles-demo.sql，先查看 articles 当前数据。
新表为空时，选中 START TRANSACTION 和整个 INSERT 执行，再查询结果。
练习数据沿用前端已有文章的编号、标题和分类，正文只提供两个简短内容块，并非完整内容迁移。

确认得到：

| id | 状态 | 发布时间 |
|---|---|---|
| article_six_types | published | 2026-11-01 09:00:00 |
| article_water_temperature | published | 2026-11-01 10:00:00 |
| article_storage | draft | NULL |

确认无误后，单独执行：

```sql
COMMIT;
```

如果插入失败或发现内容不对，在提交前执行 ROLLBACK，处理原因后再重试。
不要重复插入已提交的同一组主键，也不要使用覆盖更新绕过冲突。

## 4. 验证公开查询

```sql
SELECT id, title, category, published_at
FROM articles
WHERE status = 'published'
ORDER BY published_at DESC, id ASC;
```

本节练习数据应返回 2 行，较新的 article_water_temperature 排在前面，草稿不在结果中。
排序时间相同则比较 id，避免分页时同时间文章的相对顺序不确定。
这里尚不实现定时发布，公开可见性以 status 为准。

可选练习：

```sql
SELECT id, JSON_TYPE(content_blocks) AS body_type,
       JSON_LENGTH(content_blocks) AS block_count
FROM articles
ORDER BY id;
```

三个 body_type 应为 ARRAY，block_count 都应为 2。

## 下一步

已完成的公开分页列表见 articles-list.md，公开详情和正文 JSON 映射见 articles-detail.md。
列表不携带整篇正文，详情再返回 contentBlocks；直接请求草稿编号也不能绕过发布状态检查。
