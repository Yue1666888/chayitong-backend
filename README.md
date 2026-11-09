# 茶一统后端

茶一统微信小程序的 Java 后端。工程以 `java-web-zy-template/template` 为骨架，业务基线来自 `02-chayitong` 当前小程序代码，覆盖内容与推荐、微信用户、个人资产、社区、媒体和商城订单。

## 文档入口

- [规格说明](specs/README.md)
- [需求规格](specs/chayitong-backend/01_spec.md)
- [技术设计](specs/chayitong-backend/02_design.md)
- [实施任务](specs/chayitong-backend/03_tasks.md)
- [验收标准与记录](specs/chayitong-backend/04_verification.md)
- [API 接口文档](specs/docs/API接口文档.md)
- [MySQL 数据库设计](specs/docs/数据库设计/MySQL设计-chayitong.md)
- [业务追踪矩阵](specs/docs/业务追踪矩阵.md)

## 工程约定

- Java 25、Spring Boot 4.1.2、MyBatis-Plus、MySQL 8。
- Maven 应用位于 `chayitong-backend/`，根包为 `com.chayitong`。
- 统一 `/api`、Bearer 认证、`CommonResponse` 和 `PageResponse`。
- SQL 版本化管理，应用启动不自动修改表结构。
- 测试通过 Testcontainers 使用隔离 MySQL，不连接或清空已有练习数据库。
- DEMO 支付仅用于显式开启的开发环境，不代表真实微信资金能力。

## 常用命令

在 `chayitong-backend/` 内执行：

```bash
./mvnw.cmd test
./mvnw.cmd clean package
./mvnw.cmd spring-boot:run
```

本地完整启动见 `DOCKER.md`。生产必须配置数据库、微信、对象存储、CORS 和管理员，不使用模板默认密码。

前端内容种子发生变化时，在外层仓库重新生成并复核 SQL：

```bash
node scripts/export-backend-seeds.mjs
```

生成器要求相邻目录存在 `02-chayitong`，并锁定 57 款茶、20 篇文章、30 个商品和 15 题问卷的数量，防止意外导入不完整快照。
