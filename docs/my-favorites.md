# 查询我的收藏

本节新增 GET /api/me/favorites，调用方只传分页参数，用户编号由后端从登录身份取得。
不修改数据库结构。原有 /api/users/{userId}/favorites 接口及归属校验继续保留。

## 代码入口

- MyFavoritesController.getFavorites：通过 @AuthenticationPrincipal 获取当前 LoginUser。
- SecurityConfig：GET /api/me/favorites 必须登录。
- UserService.findFavorites：继续执行已有用户检查、计数与分页查询。

currentUser 来自 Spring Security 已验证的身份，不从 URL 或请求体绑定。
身份确认之后，通过 currentUser.getUserId() 得到业务用户编号。
即使额外传入 userId 查询参数，也不会用它决定查询哪个用户。

## 手动验证

由你启动或重新启动后端，使用 http://localhost:8080，Apifox 自动保存和发送 Cookie。
若尚未登录，先按 auth-session-login.md 获取 CSRF、登录。

1. 登录后发送 GET /api/me/favorites?page=1&pageSize=5，应返回 200。
   返回结构仍包含 user 和 favorites，user.id 应与 GET /api/auth/me 返回的 id 相同。
   收藏数量和内容以当前数据库为准，没有收藏时 items 为 []。
2. 此接口是读取请求，不需要 CSRF 请求头，不需要 Body，也不需要传 userId。
3. 可试传 userId=user002，响应仍是当前登录用户的收藏。
4. 将 page 改成 0，应返回 400 和 VALIDATION_FAILED。
5. 使用独立且没有登录 Cookie 的客户端请求，应返回 401 和 UNAUTHENTICATED。
   也可以按登录文档成功退出后，再请求此接口验证 401。

GET 接口的登录要求不因免 CSRF 校验而取消：身份校验与 CSRF 校验承担不同职责。

## 添加和取消我的收藏

本节在同一个 Controller 中增加两个写接口：

| 请求 | 结果 |
|---|---|
| PUT /api/me/favorites/{teaId} | 新增收藏返回 201，已收藏返回 204 |
| DELETE /api/me/favorites/{teaId} | 取消收藏返回 204，原本未收藏也返回 204 |

两个方法都通过 @AuthenticationPrincipal 取得当前用户，通过 @PathVariable 取得目标茶叶编号。
继续调用 UserService.addFavorite / removeFavorite，沿用事务、唯一约束与重复请求处理。
SecurityConfig 分别要求这两个路径的 PUT 和 DELETE 请求必须登录，默认 CSRF 防护继续生效。

手动操作步骤：

1. 登录后 GET /api/auth/csrf，保留 Apifox 自动管理的 Cookie，并记录返回的 headerName 和 token。
   登录会清除登录前的 CSRF 值；如果已持有登录后且仍有效的值，可以继续复用。
2. GET /api/teas 和 GET /api/me/favorites，选择一个真实存在、当前尚未收藏的茶叶编号。
   下文以 tea003 为例，应替换成你选中的编号。
3. PUT /api/me/favorites/tea003，无 Body。按 headerName 添加请求头，值为第 1 步取得的 token。
   新增成功返回 201；重复发送返回 204。GET /api/me/favorites 查看结果。
4. DELETE /api/me/favorites/tea003，携带相同 Cookie 和有效的 CSRF 请求头，无 Body。
   返回 204；重复发送仍返回 204。再次查询，确认这杯茶已不在自己的收藏中。
5. 临时关闭该写请求的 CSRF 请求头后发送，应返回 403，code 为 CSRF_FAILED。
   恢复请求头后可以继续使用，无需因为一次失败就重新登录。

额外传入 userId 不会改变实际操作的用户；用户编号始终取自登录身份。
幂等性指重复操作后数据状态一致，第一次和后续请求的 HTTP 状态码可以不同。
未登录但 CSRF 有效时返回 401；缺少或错误的 CSRF 可能更早被拦截为 403。

## 自动验证

在 Maven 根目录执行：

```powershell
mvn '-Ddb.integration=true' '-Dtest=SessionSecurityIntegrationTests' test
```

测试启用完整安全过滤器链，通过 MockMvc 运行，不启动 HTTP 服务器。
使用数据库事务内的临时用户、凭据和收藏，结束后回滚。
