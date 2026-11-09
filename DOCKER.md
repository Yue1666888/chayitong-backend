# Docker 本地运行

## 隔离原则

新 Compose 项目名为 `chayitong-backend-v2`，使用 `mysql-data` 和 `media-data` 命名卷。它不接管旧 `chayitong` 容器，也不挂载旧 `D:/docker-data/chayitong/mysql`。不要为了初始化而删除任何已有容器或卷。

## 启动数据库

在外层仓库执行：

```bash
docker compose up -d mysql
```

首次创建数据卷时依次执行：

1. `chayitong-backend/sql/bootstrap/init.sql`：24 张表。
2. `chayitong-backend/sql/bootstrap/init_data.sql`：配置和 15 题问卷。
3. `chayitong-backend/sql/bootstrap/content_seeds.sql`：前端现有内容种子。

默认宿主机 MySQL 端口为 3306。若被占用，在 `.env` 设置 `MYSQL_PORT=3307`，同时把应用的 `DB_URL` 端口改为 3307。容器网络内部始终使用 3306。

## 启动完整服务

```bash
docker compose --profile backend up --build -d
```

应用默认绑定 `127.0.0.1:8080`，健康检查：

```bash
curl http://localhost:8080/api/health
```

Compose 默认使用 dev profile、本地媒体目录和数据库初始化种子。真实微信登录需要配置 `WECHAT_MINIAPP_APP_ID/WECHAT_MINIAPP_APP_SECRET`；没有有效配置时登录会失败，不会伪造用户。

DEMO 支付只有在环境变量 `APP_SHOP_DEMO_PAYMENT_ENABLED=true` 时可用，且永远标记为演示订单。生产 profile 强制关闭。

## 配置与秘密

复制 `chayitong-backend/.env.example` 为 `chayitong-backend/.env` 后填写。PowerShell 可使用现有包装脚本：

```powershell
./compose.ps1 config --quiet
./compose.ps1 up -d mysql
./compose.ps1 --profile backend up --build -d
```

不要把 `compose config` 的完整输出、数据库密码、AppSecret、访问令牌或 S3 密钥提交到 Git 或写入文档。

## 腾讯云 COS 生产存储

生产 profile 默认 `APP_MEDIA_STORAGE=cos`，必须配置：

```dotenv
APP_COS_SECRET_ID=由密钥系统注入
APP_COS_SECRET_KEY=由密钥系统注入
APP_COS_REGION=ap-beijing
APP_COS_BUCKET=实际桶名
APP_COS_PREFIX=picgo/
APP_MEDIA_PUBLIC_BASE_URL=https://实际API域名/api/media
```

应用通过 COS SDK 上传和读取，公网媒体仍由 `/api/media/{id}/content` 提供。MySQL 端口仅绑定本机回环地址，不需要为 COS 在 Docker 内启动额外服务。`compose.storage.yaml` 仅为可选 SeaweedFS 开发适配，不属于 COS 生产依赖。

## 停止与重建

```bash
docker compose --profile backend stop
docker compose --profile backend start
docker compose --profile backend logs --tail 100 app
```

停止不会删除数据。`docker compose down` 默认保留命名卷；不要添加 `-v`，除非明确要永久删除这个新项目的隔离数据并已完成备份。

## 调试

```bash
docker compose -f compose.yaml -f compose.debug.yaml --profile backend up --build -d
```

调试端口仅映射至 `127.0.0.1:5005`。Java 版本为 25，应用构建产物为 `target/app.jar`。
