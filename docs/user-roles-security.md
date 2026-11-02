# 角色与权限，第 2 步：Java 接入

前提：按 user-roles.md 增加 users.role 并提交管理员设置。
本次已确认 xiaolin 为 ADMIN，xiaoming 为 USER。

## 角色如何进入登录身份

1. 登录时，DatabaseUserDetailsService 调用 UserCredentialRepository.findByLoginName。
2. SQL 联查 users 和 user_credentials，将 u.role 一起读取。
3. Repository 把 role 传给 LoginUser 的构造方法。
4. LoginUser.getAuthorities() 返回 ROLE_ + role 对应的权限。
5. 登录成功后，身份与角色保存在当前会话的安全上下文中，后续请求使用该登录身份。

| 数据库值 | 框架中的 GrantedAuthority | 对应的角色检查 |
|---|---|---|
| USER | ROLE_USER | hasRole("USER") |
| ADMIN | ROLE_ADMIN | hasRole("ADMIN") |

hasRole 默认补上 ROLE_ 前缀，所以参数写 ADMIN；不要写 hasRole("ROLE_ADMIN")。
SimpleGrantedAuthority 是框架表示一项权限的类，本节先理解其保存的字符串即可。
管理员本节只具有 ROLE_ADMIN，收藏接口要求已登录，因此管理员仍可管理自己的收藏，无需配置角色继承。

## 当前接口规则

| 接口 | 权限要求 |
|---|---|
| GET/HEAD /api/teas、/api/teas/** | 公开查询 |
| POST /api/teas | 管理员新增茶叶 |
| POST /api/teas/batch | 管理员批量新增 |
| POST /api/teas/preview | 管理员课堂预览，不保存数据 |
| PUT /api/teas/{id} | 管理员修改 |
| DELETE /api/teas/{id} | 管理员删除 |
| /api/me/favorites 相关请求 | 已登录用户管理自己的收藏 |
| /api/users/{userId}/favorites 相关请求 | 登录身份的 userId 必须与路径一致 |

管理员没有绕过收藏归属校验的特例。
写请求继续需要有效 CSRF 请求头，包括管理员请求。
角色来源是数据库登录查询，不接受客户端 X-Role 请求头或 role 查询参数作为授权依据。
注册 DTO 也没有 role 字段，数据库为普通注册设置默认 USER；额外发送 role=ADMIN 不会获得管理员身份。
接口响应目前继续使用 UserResponse，只包含 id、displayName。

## Apifox：先用无数据库写入的请求比较角色

由你重新启动后端；重启后原会话需要重新登录。
统一使用 http://localhost:8080，Apifox 自动管理 Cookie。

### 普通用户

1. GET /api/auth/csrf，取得登录前 CSRF。
2. POST /api/auth/login，Body 为 x-www-form-urlencoded，username=xiaoming，password 使用你的原密码。
   带第 1 步 CSRF 请求头。成功返回 200。
3. 再 GET /api/auth/csrf，取得登录后的有效值。
4. POST /api/teas/preview，带 Cookie 和第 3 步 CSRF，请求 Body 选择 JSON：

```json
{"name":"权限练习茶","type":"绿茶"}
```

应返回 403，code 为 FORBIDDEN，message 为 当前账号无权执行此操作。
如果返回 CSRF_FAILED，应先检查 CSRF 和 Cookie，尚不能据此判断角色权限。
GET /api/teas 和 GET /api/me/favorites 仍应成功。

### 管理员

1. 使用当前有效 CSRF 请求头，POST /api/auth/logout，正常退出普通账号。
2. GET /api/auth/csrf，重新取得登录前校验值。
3. POST /api/auth/login，username=xiaolin，使用该账号的原密码和第 2 步 CSRF。
4. 登录成功后 GET /api/auth/csrf。
5. 发送相同 POST /api/teas/preview JSON，携带当前 Cookie 和第 4 步 CSRF。

应返回 200 和预览茶叶信息；这个接口不保存数据。
如果关闭 CSRF 请求头再发送，即使管理员已登录，也应得到 403 / CSRF_FAILED。

## 角色修改与会话

本项目登录时查库，普通业务请求不重新查询角色。
修改 users.role 不会自动刷新现有 Session 中的身份：退出后重新登录才读取新角色。
提升和降低角色均如此。实时强制下线属于后续独立功能。
只重新获取 CSRF 不会刷新角色。

## 自动验证

在 Maven 根目录执行：

```powershell
mvn '-Ddb.integration=true' '-Dtest=SessionSecurityIntegrationTests,RegistrationIntegrationTests' test
```

MockMvc 启用完整安全过滤器链，不启动 HTTP 服务器。
权限测试的用户、茶叶和角色修改都在测试事务内回滚。
注册测试验证提交后清理自己生成的临时账号，不改变你的真实账号角色。

验证覆盖普通用户和访客不能维护茶叶、管理员可以新增/修改/批量新增/删除、
管理员仍需 CSRF、收藏归属规则、注册不能指定管理员角色、下次登录才读取角色变更。
