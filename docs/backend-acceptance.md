# Java 后端入门验收

## 本地运行

项目用 Java 25、Maven 和 Docker 中的 MySQL 8.0。验收时本机使用 Java 25.0.1、Maven 3.9.16 和 MySQL 8.0.46。

1. 在 Docker Desktop 中确认 `chayitong` 容器正在运行。
2. 在后端仓库外层目录运行 `.\scripts\Test-DatabaseConnection.ps1`，确认应用账号能通过 `127.0.0.1:3309` 连接 `chayitong` 库。
3. 进入有 `pom.xml` 的内层 `chayitong-backend` 目录。真实 `.env` 放在这一层，应用通过 `MYSQL_APP_PASSWORD` 读取数据库密码。
4. 由你在 IDE 启动 `ChayitongBackendApplication`，或者在 Maven 目录执行 `mvn spring-boot:run`。
5. Apifox 的请求地址统一使用 `http://localhost:8080`。先请求 `GET /api/health` 确认应用在运行，再请求 `GET /api/teas?pageSize=1` 确认茶叶查询能访问数据库。

## 数据库准备

已有练习库可用 `SHOW TABLES;` 核对。新建空库时，在 DBCode 选中目标库，按下表顺序手动执行建表语句。

| 顺序 | SQL | 结果 |
|---|---|---|
| 1 | [01-core-schema.sql](sql/01-core-schema.sql) | 建立 `teas`、`users`、`tea_favorites` |
| 2 | [04-user-credentials.sql](sql/04-user-credentials.sql) | 建立 `user_credentials` |
| 3 | [05-user-roles.sql](sql/05-user-roles.sql) | 为 `users` 添加 `role` 字段 |
| 4 | [06-articles.sql](sql/06-articles.sql) | 建立 `articles` |
| 5 | [09-tea-cover-key.sql](sql/09-tea-cover-key.sql) | 给 `teas` 增加封面对象 Key 字段 |
| 6 | [11-community-posts.sql](sql/11-community-posts.sql) | 建立茶友圈的 `posts` 表 |

在空库里，可用[注册流程](auth-registration.md)建立练习账号，再按[角色练习](user-roles.md)用实际用户编号设置管理员。需要文章样例时使用 [07-articles-demo.sql](sql/07-articles-demo.sql) 并提交事务。茶叶样例可选用 `temp.sql` 中的茶叶插入语句。五张表的结构已通过现有库上的测试核对，空库建表顺序留作手动练习。

## 自动验证

在内层 Maven 目录执行：

```powershell
mvn '-Ddb.integration=true' test
```

- 2026-11-04 在现有 MySQL 库运行全量测试，112 项通过，失败、错误和跳过均为 0。
- 覆盖茶叶增删改查、分页搜索、批量事务、注册登录、角色和 CSRF、收藏归属与幂等性，以及文章的草稿到发布流程。
- 自动测试使用 MockMvc 和真实 MySQL。另对已运行的 8080 服务发起公开 GET 检查，`/api/health`、`/api/teas?pageSize=1` 和 `/api/articles?pageSize=1` 均返回 `200`。

## Apifox 手动验收

在 Apifox 启用 Cookie 自动保存和发送。先 GET `/api/auth/csrf` 取得请求头名和值，再用表单提交 `POST /api/auth/login`。登录成功后重新 GET `/api/auth/csrf`。同一登录会话的写请求可以复用有效值。切换账号时用当前有效的 CSRF 值退出，再按同样顺序登录。

| 验收项 | 操作 | 通过条件 |
|---|---|---|
| 公开查询 | 请求 `GET /api/teas?pageSize=1` 和 `GET /api/articles?pageSize=1` | 均为 `200`，响应有 `total` 和 `items` |
| 普通用户登录 | 用 `xiaoming` 登录后 GET `/api/auth/me` 和 `/api/database/check` | 分别得到当前用户资料和数据库名 `chayitong` |
| 收藏 | 从茶叶列表选一个尚未收藏的编号，按[收藏练习](my-favorites.md)添加、查回、取消 | 首次添加 `201`，重复添加 `204`，取消后列表恢复 |
| 管理权限 | 仍用普通用户请求 `GET /api/admin/articles/{id}`，填已知的文章编号 | `403 / FORBIDDEN` |
| 文章生命周期 | 切换到 `xiaolin`，用新建的临时文章跑[综合练习](backend-learning-roadmap.md#文章综合练习) | 能读回编辑结果，发布后公开，撤回后隐藏，删除后管理详情也返回 `404` |
| 退出 | 带有效 CSRF POST `/api/auth/logout`，再 GET `/api/auth/me` | 分别返回 `204` 和 `401` |

## 看响应定位问题

| 现象 | 优先检查 |
|---|---|
| 无法连接 8080 | Java 应用是否已启动，端口是否一致 |
| 健康接口为 200，数据库查询失败 | Docker 容器、3309、应用账号、选库和表结构 |
| `400 / VALIDATION_FAILED` | 响应里的 `fieldErrors`，以及请求参数和 JSON 字段类型 |
| `401 / UNAUTHENTICATED` | Apifox 是否保存并发送当前会话 Cookie |
| `403 / CSRF_FAILED` | 是否携带本次登录后的 token，并与同一会话 Cookie 配套 |
| `403 / FORBIDDEN` | 当前账号角色或资源归属 |
| `404 / HTTP_404` | 路径、编号与公开可见状态 |
| `409` | 响应错误码对应的业务状态，例如已发布文章需要先撤回 |

你在 Apifox 复跑通过后，可以在[学习进度表](backend-learning-roadmap.md)勾选手动验收。后续从百科和首页的数据契约开始，按路线补齐小程序需要的接口。
