# 茶一统 Java 应用

## 构建与测试

```bash
./mvnw.cmd -DskipTests compile
./mvnw.cmd test
./mvnw.cmd clean package
```

测试通过 Testcontainers 启动 MySQL 8.4 并执行 `sql/bootstrap/init.sql`，需要可用 Docker，不连接本机已有数据库。

## 本地运行

1. 在外层仓库执行 `docker compose up -d mysql`，首次初始化命名卷。
2. 按 `.env.example` 准备本地环境变量，或使用 IDE 的 env-file 插件加载 `.env`。
3. 执行 `./mvnw.cmd spring-boot:run`，默认 dev 环境连接 `localhost:3306/chayitong`。
4. 访问 `GET /api/health`。

也可以在外层仓库执行 `docker compose --profile backend up --build -d`，同时构建应用和启动数据库。应用端口只绑定本机回环地址。

## SQL

- `bootstrap/init.sql`：全新数据库完整结构。
- `bootstrap/init_data.sql`：初始运营配置和必要种子。
- `schema/00_chayitong_schema.sql`：当前完整结构基线。
- `migrations/`：已有数据库的增量变更。
- `maintenance/`：需人工确认的一次性维护脚本。

应用不会自动执行这些 SQL。不要在未备份时清理 Compose 数据卷。

详细业务与接口见外层 `specs/`。
