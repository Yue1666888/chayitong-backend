# Session 登录：本节手动验证

## 准备

本节不自动创建登录账号，也不修改 .env 中的数据库密码。
如果 user_credentials 为空，需要先由你在 DBCode 录入并提交练习凭据。
参考之前的建表脚本 docs/sql/04-user-credentials.sql 和 PasswordDemo。
给 user001 设置登录名 xiaolin，并保存完整的 {bcrypt} 编码结果。
登录时使用生成这个编码前的原密码，不使用哈希字符串或 MySQL 密码。

如果凭据已经存在，直接进入登录步骤，不要重复插入。表已经存在时不用重新 CREATE。
运行 PasswordDemo 后，复制从 {bcrypt} 开始的完整编码，
替换下面的占位文字后，在 DBCode 执行并提交：

```sql
INSERT INTO user_credentials (user_id, login_name, password_hash)
VALUES ('user001', 'xiaolin', '<替换为完整密码编码>');
```

检查时只查询 user_id、login_name 和编码格式，不需要把完整哈希发到聊天里。

新增了 Spring Security 完整依赖，先刷新 Maven，再由你重启后端。
下面所有请求统一使用 http://localhost:8080，避免混用 localhost 与 127.0.0.1 导致 Cookie 不匹配。
Apifox 保持自动保存、发送 Cookie。

## 三种凭据的职责

| 名称 | 职责 | 保存位置 |
|---|---|---|
| 业务用户密码 | 登录时验证身份 | 客户端输入原密码，数据库保存哈希 |
| JSESSIONID Cookie | 后续请求携带已登录会话 | 服务端生成，Apifox 自动管理 |
| CSRF token | 校验本次写请求来自持有此会话的客户端 | 从 csrf 接口取得，放入请求头 |

Cookie 和 CSRF 值都不用添加到 .env，也不要把它们贴到聊天里。

## 1. 获取登录前的 CSRF 校验值

GET /api/auth/csrf

返回 headerName、parameterName、token。保留 Cookie，记录 headerName 和 token。
headerName 通常是 X-CSRF-TOKEN。后续请求用该名称作为请求头，token 作为值。

## 2. 登录

POST /api/auth/login

Body 选择 x-www-form-urlencoded：

- username：xiaolin（对应数据库 login_name）
- password：你生成哈希时使用的原密码

请求头带第 1 步的 CSRF 校验值，Cookie 由 Apifox 自动发送。
注意：本节采用 Spring Security 的表单认证过滤器，登录 Body 不使用 JSON。
成功返回 200 和用户 id、displayName。错误账号或密码统一返回 JSON 401。
没有 CSRF 校验值会先返回 JSON 403，code 为 CSRF_FAILED。

## 3. 查看当前身份和收藏

GET /api/auth/me

登录后仅携带 Cookie，即可得到自己的业务用户资料。未登录返回 JSON 401。
GET /api/users/user001/favorites 返回自己的收藏。
登录为 user001 时，访问 user002 的收藏返回 JSON 403，code 为 FORBIDDEN。
归属校验比较业务 user_id，不把登录名 xiaolin 当成 user001。

## 4. 登录后重新获取 CSRF，再修改收藏

再次 GET /api/auth/csrf，使用登录后的新 token。登录成功会清除旧 CSRF 值。

PUT /api/users/user001/favorites/tea003
DELETE /api/users/user001/favorites/tea003

这两个请求都不需要 Body，需携带 Cookie 和新的 CSRF 请求头。
对应会话有效且 CSRF 未被重置时，可复用这个值；登录成功、退出或会话失效后需要重新获取。
即使 CSRF 正确，也不能操作其他用户的收藏。

## 5. 退出

POST /api/auth/logout，携带 Cookie 和当前 CSRF 请求头。
返回 204，服务端会话失效并清除 Cookie。再 GET /api/auth/me 得到 401。
重新登录前再 GET /api/auth/csrf。

## 当前权限范围

本节保护 /api/auth/me、/api/database/check 和 /api/users/**；收藏只能由对应的登录用户访问。
茶叶 GET/HEAD 查询公开，茶叶 POST、PUT、DELETE 请求要求 ADMIN 角色，包括批量新增和课堂 POST 预览。
所有 POST、PUT、DELETE 等写请求仍需 CSRF 校验；管理员也需要携带有效 CSRF 值。
角色从 users.role 读取；修改数据库角色后，退出并重新登录才能更新会话中的角色。
具体角色验证步骤见 user-roles-security.md。
本节使用内存 Session，重启后端需要重新登录。

## 测试

mvn -Ddb.integration=true test

原业务 SQL/事务测试关闭 MockMvc 过滤器，专注业务行为。
SessionSecurityIntegrationTests 开启完整过滤器链，真实验证登录、会话、归属与 CSRF。
安全测试只使用事务内的临时用户和凭据，结束后回滚。
