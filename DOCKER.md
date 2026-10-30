# 茶一统本地 Docker 配置

## 配置入口

外层目录包含 compose.yaml、compose.debug.yaml、Dockerfile 和 compose.ps1。
内层 chayitong-backend 是 Maven 项目，包含 pom.xml 和真实 .env。
compose.ps1 会自动定位两处文件，避免 Compose 默认找不到内层 .env。
以下命令在本文件所在的外层目录执行：

```powershell
.\compose.ps1 config --quiet
.\scripts\Test-DatabaseConnection.ps1
```

解析后的完整配置包含密码，不要将 `compose config` 的完整输出贴到聊天或提交到 Git。
只检查语法时使用 `config --quiet`。

## 当前数据库

- 手动容器 chayitong 使用 MySQL 8.0.46；配置固定使用同一镜像 digest，不升级数据库。
- 本机地址为 127.0.0.1:3309，容器内端口为 3306。
- 数据库和普通账号均为 chayitong，密码来自内层 .env 的 MYSQL_APP_PASSWORD。
- 数据目录 D:/docker-data/chayitong/mysql 挂载到 /var/lib/mysql，沿用已有数据。
- MYSQL_USER、MYSQL_PASSWORD 等初始化变量只对首次使用的空数据目录生效，不会修改已有账号密码。
- 真实 .env 保留原值，仍由内层项目的 .gitignore 排除。

DBCode 填写 MySQL、127.0.0.1、3309、账号 chayitong、数据库 chayitong。
目前还没有业务表，建表和练习 SQL 仍由你手动完成。

## 账号、名称与密码对应关系

- 容器名 chayitong：Docker 管理的运行实例，与数据库账号无关。
- 数据库名 chayitong：存放项目表和数据的库，与同名账号无关。
- MySQL 账号 root：管理员，密码对应 .env 的 MYSQL_ROOT_PASSWORD。
- MySQL 账号 chayitong：项目账号，密码对应 .env 的 MYSQL_APP_PASSWORD。
- DBCode 中的连接名称只是显示标签，登录数据库时填写的 Username 才是实际账号。
- Docker Desktop 登录账号用于 Docker 产品服务，不是 MySQL 账号。
- MYSQL_APP_PASSWORD、MYSQL_ROOT_PASSWORD 是配置变量名，不是账号名。

真实密码只维护在内层 .env 中，不再使用额外的 DB_PASSWORD 别名。
application.properties 通过 spring.config.import 显式读取 .env，并使用
spring.datasource.password=${MYSQL_APP_PASSWORD} 引用项目密码。
本地 .env 采用 KEY=value 的 properties 兼容写法，不在值外加引号。
修改配置后，需要重新启动已在运行的 Java 应用。
Java、DBCode 和连接检查脚本都使用相同的项目账号和密码。

## 手动容器如何交给 Compose 管理

新增配置不会自动接管 Docker Desktop 手动创建的容器。
本次保留手动容器运行，没有执行下面的迁移命令。
同名手动容器仍存在时，Compose Up 会报告容器名冲突。

迁移时先停止旧容器，重命名保留，再让 Compose 使用原数据目录创建新容器：

```powershell
docker stop chayitong
docker rename chayitong chayitong-manual-backup
.\compose.ps1 up -d mysql
.\compose.ps1 ps
.\compose.ps1 logs --tail 30 mysql
.\scripts\Test-DatabaseConnection.ps1
```

新旧容器使用同一个数据目录，旧的备份容器必须保持停止，不能同时启动。
启动失败时，按以下顺序回到旧容器：

```powershell
.\compose.ps1 stop mysql
.\compose.ps1 rm -f mysql
docker rename chayitong-manual-backup chayitong
docker start chayitong
```

回滚只移除新建容器，不删除 D:/docker-data/chayitong/mysql。

## 接管后的常用命令

```powershell
.\compose.ps1 up -d mysql
.\compose.ps1 ps
.\compose.ps1 logs --tail 30 mysql
.\compose.ps1 stop mysql
.\compose.ps1 start mysql
```

MySQL 健康检查通过普通账号执行 SELECT 1，检查库和账号确实可用。

## 后续容器化 Java

Java 服务在 backend profile 中，默认只启动数据库。
Dockerfile 使用 Java 25，与项目版本一致；Java 端口为 8080。
先在内层 Maven 目录运行 `mvn package`，再回到外层目录运行：

```powershell
.\compose.ps1 --profile backend up -d --build
```

如果 JAR 被运行中的应用占用，先停止该应用再打包。
容器中的 Java 连接 mysql:3306，本机 DBCode 继续连接 127.0.0.1:3309。
Compose 通过 SPRING_DATASOURCE_PASSWORD 提供同一个 MYSQL_APP_PASSWORD，和本机 Java 使用同一份密码。

调试文件是覆盖配置，需要与基础配置一起使用：

```powershell
.\compose.ps1 -f compose.debug.yaml --profile backend up -d --build
```

调试端口只映射到本机 5005。当前没有构建或启动 Java 镜像。
