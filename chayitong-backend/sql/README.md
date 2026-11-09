# SQL 目录

本目录对应 `specs/docs/数据库设计/MySQL设计-chayitong.md`，目标版本为 MySQL 8.x。建表使用 InnoDB、`utf8mb4`，`CHECK` 约束需要 MySQL 8.0.16 或更高版本才会强制执行。

## 文件与执行顺序

- `schema/00_chayitong_schema.sql`：当前完整的 24 表结构；会创建并切换到 `chayitong` 数据库，供手工初始化或核对结构。
- `bootstrap/init.sql`：全新环境建库和建表，内容与当前 schema 基线一致。
- `bootstrap/init_data.sql`：最小初始数据，包含 `production` 配置占位和完整的 `v1` 十五题问卷及评分规则。
- `bootstrap/content_seeds.sql`：由 `scripts/export-backend-seeds.mjs` 从前端当前种子生成，包含 57 款茶、20 篇文章、30 个商品、40 条社区动态和演示作者；使用 `INSERT IGNORE` 保留已有业务修改。
- `migrations/001_initial_schema.sql`：初始基线说明。后续结构变更新增三位递增序号脚本，不修改已经发布或执行的迁移。
- `maintenance/`：修复、归档等一次性维护脚本；当前没有维护脚本，应用也不会自动执行该目录。

全新数据库按以下顺序执行：

```bash
mysql -u <user> -p < chayitong-backend/sql/bootstrap/init.sql
mysql -u <user> -p < chayitong-backend/sql/bootstrap/init_data.sql
mysql -u <user> -p < chayitong-backend/sql/bootstrap/content_seeds.sql
```

只需表结构时，可改为执行 `schema/00_chayitong_schema.sql`。初始化和内容种子可重复执行，已有 `production` 配置、`v1` 问卷或相同业务键的内容不会被强制覆盖。脚本不会删除数据库、清空表格或迁移既有数据。

## 写入约定

- 所有表都有非自增 `BIGINT id`，由应用生成并显式写入雪花 ID。
- `created_at`、`updated_at` 使用 `DATETIME(6)`，默认由数据库以 UTC 微秒时间填充，更新时自动刷新 `updated_at`；应用仍可显式赋值。连接和脚本会话均使用 UTC。
- `deleted` 为 `INT`，默认 `0`，仅允许 `0/1`。默认业务查询必须过滤 `deleted = 0`。
- 收藏、浏览历史、点赞和购物车等业务唯一键不包含 `deleted`。取消时更新原行，再次操作时恢复原行，避免删除后重建造成唯一键冲突或重复墓碑行。
- 业务标识、摘要哈希和交易号使用 `ascii_bin`；对象键全局唯一，同时也满足本地对象键唯一要求。
- 多类型目标、JSON 内公开 ID 和媒体通用绑定是逻辑关联，由服务层校验；普通单体物理外键统一为 `ON DELETE RESTRICT ON UPDATE RESTRICT`。
- 金额以 BIGINT 分存储，币种为 CNY。数据库约束兜底金额、库存、数量、计数、状态和 JSON 容器类型，复杂内容结构及跨行一致性仍由应用事务保证。
