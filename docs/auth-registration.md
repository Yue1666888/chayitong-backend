# 注册账号：本节练习

接口：POST /api/auth/register。注册创建业务账号，不创建 MySQL 用户，不修改 .env。
使用已有 users、user_credentials 表，不需要改表。

## 参数

Body 选择 JSON：

```json
{
  "loginName": "xiaoming",
  "password": "LearnJava2026!",
  "displayName": "小明"
}
```

以上密码仅用于本地练习；你可以自行换成符合规则的练习密码。

- loginName：3 到 32 个英文字母、数字或下划线；唯一性以数据库为准。
  当前表的排序规则不区分大小写，xiaoming 和 XiaoMing 不能分别注册。
- password：本节支持 8 到 64 个 ASCII 可打印字符，包括英文字母、数字、英文符号和空格；不能全为空白。
  这是本节的接口约定，不是 Java 的限制。采用该范围是为了避开 bcrypt 的 72 字节输入上限。
  后端直接编码原始密码，不自动去除密码首尾空格。
- displayName：必填，不能全为空白，最多 50 个字符，支持中文。
- 用户 id 由后端 UUID.randomUUID() 生成，不需要由客户端提供。

注册成功返回 201，只包含 id、displayName，不返回密码或密码哈希。
注册不会自动登录；未登录客户端注册后，请求 /api/auth/me 仍得到 401。
注册和登录的 Body 格式不同：注册使用 JSON，当前登录接口使用 x-www-form-urlencoded。

## 手动步骤

1. 由你重新启动后端，Apifox 统一使用 http://localhost:8080，并自动管理 Cookie。
2. 建议使用没有登录身份的客户端；如已登录，可先按登录文档正常退出。
3. GET /api/auth/csrf，记录 headerName 和 token，保留 Cookie。
4. POST /api/auth/register，发送上面的 JSON，并按 headerName 携带 token 请求头。
5. 返回 201，记下 id。重复注册相同 loginName，返回 409 / DATA_CONFLICT。
6. 可在 DBCode 检查数据：

```sql
SELECT u.id, u.display_name, c.login_name,
       LEFT(c.password_hash, 8) AS hash_prefix,
       CHAR_LENGTH(c.password_hash) AS hash_length
FROM users AS u
JOIN user_credentials AS c ON c.user_id = u.id
WHERE c.login_name = 'xiaoming';
```

当前编码前缀应为 {bcrypt}，长度为 68。不要把完整哈希或实际密码贴到聊天中。

7. 发送 POST /api/auth/login，Body 选择 x-www-form-urlencoded：
   username=xiaoming，password=注册时输入的原密码，携带 Cookie 和有效 CSRF 请求头。
   注册本身不重置 CSRF，所以此前取得的值仍有效时可以复用。
8. 登录成功后 GET /api/auth/csrf，获取登录后的新值。
   GET /api/me/favorites 应显示这个新用户，初始收藏为空。

## 业务顺序

RegisterRequest 校验 → AuthController → RegistrationService → 两个 Repository。

RegistrationService.register 带 @Transactional：

1. UUID 生成业务用户编号。
2. PasswordEncoder.encode 将原密码转成带 {bcrypt} 前缀的密码哈希。
3. INSERT users，保存资料。
4. INSERT user_credentials，保存登录名和密码哈希。
5. 返回 UserResponse，事务提交成功后，Controller 返回 201。

如果第 4 步登录名重复，数据库唯一约束引发 DuplicateKeyException，异常离开 Service，
事务回滚第 3 步，再由已有 ApiExceptionHandler 返回 409。
不会覆盖原账号密码，也不会遗留一个只有资料、没有凭据的新用户。

permitAll 表示注册不要求登录；默认 CSRF 校验继续生效。
缺少 CSRF 返回 403，参数不合法返回 400，重复登录名返回 409。

## 自动验证

```powershell
mvn '-Ddb.integration=true' '-Dtest=RegistrationIntegrationTests,SessionSecurityIntegrationTests' test
```

使用完整安全过滤器链和真实 MySQL，不启动 HTTP 服务。
注册测试不包裹测试事务，从而验证业务提交和失败回滚；测试产生的唯一临时账号在结束后删除。
不使用你的已有账号或密码。
