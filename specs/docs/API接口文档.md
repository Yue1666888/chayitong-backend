# 茶一统后端 API 接口文档

## 1. 文档说明

本文依据 `specs/README.md`、`specs/chayitong-backend/01_spec.md` 与 `specs/docs/modules/*.md` 编写，是公开端、登录用户端和管理端 HTTP 接口的人工契约说明。

- 所有业务接口统一使用 `/api` 前缀，不设置其他版本前缀。
- 请求与响应默认使用 UTF-8 JSON；文件上传使用 `multipart/form-data`，文件内容读取返回二进制。
- 时间使用带时区的 ISO 8601 字符串，例如 `2026-11-08T12:00:00+08:00`；每日积分业务日期使用 `YYYY-MM-DD`，时区为 `Asia/Shanghai`。
- 数据库雪花 Long ID 在 JSON 中统一序列化为字符串，包括用户、茶、文章、商品、SKU、购物车行、动态、评论和媒体 ID。订单号 `orderNo`、推荐结果 `resultId`、slug 本身也是字符串。
- 金额均为整数分，字段以 `Fen` 结尾；币种固定为 `CNY`。
- 首版履约仅支持到店自取，配送费为 0，不采集收货地址，不包含优惠券、物流、退款和售后。
- 真实微信支付通知仅作预留，不表示首版已经具备真实扣款能力。DEMO 支付只在显式开发开关下可用，生产默认关闭，未配置真实支付时不得自动降级为演示成功。

### 1.1 接口数量

本文共列出 **70 个 HTTP 端点**：68 个模块明确端点、1 个模块允许的可选动态隐藏/恢复管理端点、1 个预留真实微信通知端点。

| 模块 | 数量 |
| --- | ---: |
| 认证、用户与个人资产 | 14 |
| 内容、首页与百科 | 19 |
| 搜索、荐茶与答题 | 4 |
| 社区动态与互动 | 11 |
| 媒体文件 | 2 |
| 商城与订单 | 20 |

## 2. 通用契约

### 2.1 身份与权限

| 权限标记 | 含义 |
| --- | --- |
| 公开 | 无需登录；可自愿携带有效访问令牌以取得 `liked` 等个人视图 |
| 用户 | 必须携带有效 Bearer token，只能操作认证上下文中的本人资源 |
| 管理员 | 必须携带有效 Bearer token，且服务端角色为 `ADMIN` |

认证头格式：

```http
Authorization: Bearer <opaque-access-token>
```

访问令牌是服务端生成的高强度 opaque token，不是用户 ID、openid 或客户端自行构造的 JWT。openid、session_key、AppSecret 和令牌摘要不会出现在业务响应中。公开接口如果显式携带了无效或过期令牌，仍返回 401，不按游客静默处理。

所有 `/api/me/**`、购物车、订单接口均忽略或拒绝请求中冒用的 `userId`。管理员身份只来自服务端受控数据，客户端不能通过请求指定角色。

### 2.2 CommonResponse

除媒体二进制内容和未来接入的第三方通知协议响应外，所有业务响应统一为：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | string | 成功固定为 `00000`；失败为稳定业务错误码 |
| `message` | string | 可供调试或展示的消息，不用于程序分支 |
| `data` | any/null | 成功数据；失败通常为 `null` |
| `timestamp` | string | 服务端 ISO 8601 时间 |
| `traceId` | string | 本次请求链路 ID |

成功示例：

```json
{
  "code": "00000",
  "message": "success",
  "data": {
    "id": "1938572012345678900"
  },
  "timestamp": "2026-11-08T12:00:00+08:00",
  "traceId": "4e851df929aa4cf7"
}
```

失败示例：

```json
{
  "code": "IDEMPOTENCY_CONFLICT",
  "message": "同一幂等键不能用于不同请求",
  "data": null,
  "timestamp": "2026-11-08T12:00:00+08:00",
  "traceId": "4e851df929aa4cf7"
}
```

### 2.3 分页

分页查询参数：

- `pageNum`：从 1 开始，默认 1。
- `pageSize`：默认 20；服务端设有上限，非法值或超过上限返回 400。

所有分页 `data` 统一为：

```json
{
  "list": [],
  "total": 0,
  "pageNum": 1,
  "pageSize": 20
}
```

`total` 是与当前筛选条件匹配的总数，不是当前页条数。各模块按文档规定的稳定次序排序，客户端不得在请求中覆盖公开状态过滤。

### 2.4 HTTP 状态与业务错误码

| HTTP | 常见 `code` | 含义 |
| --- | --- | --- |
| 200 | `00000` | 查询、修改、删除或幂等重复操作成功 |
| 201 | `00000` | 首次创建资源成功；创建接口也可按统一实现使用 200，语义以响应数据为准 |
| 400 | `VALIDATION_ERROR` | 参数、JSON、枚举、内容块、文件内容或问卷答案非法 |
| 400 | `INVALID_FILE` | 文件扩展名、MIME、实际文件头或解码结果不合法 |
| 401 | `UNAUTHORIZED` | 未认证、访问/刷新令牌无效或过期、微信登录 code 无效 |
| 403 | `FORBIDDEN` | 已认证但无角色或资源操作权限 |
| 404 | `NOT_FOUND` | 资源不存在、公开不可见，或为隐藏他人私有资源 |
| 409 | `IDEMPOTENCY_CONFLICT` | 同一幂等键对应不同规范化请求 |
| 409 | `STATE_CONFLICT` | 当前资源状态不允许该操作 |
| 409 | `QUOTE_EXPIRED` / `QUOTE_CHANGED` | 报价失效，或价格、库存等已经变化 |
| 409 | `INVENTORY_INSUFFICIENT` | 可用库存不足或并发预占失败 |
| 413 | `PAYLOAD_TOO_LARGE` | 上传文件或请求体超过限制 |
| 503 | `PAYMENT_UNAVAILABLE` | 真实支付适配器未配置或当前支付能力不可用 |
| 503 | `STORAGE_UNAVAILABLE` | 对象存储配置或服务不可用 |
| 500 | `INTERNAL_ERROR` | 未预期服务端错误；响应不泄露内部秘密 |

校验错误可能使用更具体的稳定错误码，但必须保持正确 HTTP 状态。业务成功不通过“HTTP 200 内藏失败”表达。

### 2.5 通用幂等原则

- 收藏、点赞等表达最终状态的 PUT/DELETE 不使用 toggle；重复请求保持最终状态，不重复改变计数。
- 创建动态、评论和订单使用 `clientRequestId`。同身份、同键、同规范化请求返回首次结果；同键不同内容返回 409 `IDEMPOTENCY_CONFLICT`。
- 普通 GET 不产生业务写入；浏览历史只能由客户端显式调用历史写入接口产生。
- 删除、清理与状态迁移不得重复扣减计数或重复释放库存。

## 3. 认证、用户与个人资产

### 3.1 数据结构与规则

`UserProfile`：

```json
{
  "id": "1938572012345678900",
  "displayName": "茶友",
  "avatar": "https://cdn.example.com/media/avatar.webp",
  "bio": "一盏清茶",
  "favoriteTea": "绿茶",
  "role": "USER",
  "points": 125,
  "level": {
    "index": 2,
    "name": "闻香识茶",
    "description": "当前等级说明",
    "points": 125,
    "nextName": "一盏知春",
    "nextThreshold": 250,
    "remainingPoints": 125,
    "percent": 50,
    "maxed": false
  }
}
```

九级积分门槛依次为 `0、100、250、450、700、1000、1400、2000、3000`；等级名依次为 `初识茶味、闻香识茶、一盏知春、茶席常客、辨味识韵、壶中岁月、茶事精进、通晓茶性、茶中博士`。3000 分后 `maxed=true`、`percent=100`。`points`、`level`、`lastDailyAt` 只能由服务端计算。

收藏和历史目标类型 `type` 均为 `tea/article/product/post`。失效目标只返回不可访问标记，不泄露草稿或下架详情。

### 3.2 `POST /api/auth/wechat/login`

- **权限**：公开。
- **请求**：

```json
{
  "code": "wx.login 返回的一次性 code"
}
```

- **data**：`accessToken`、`refreshToken`、`tokenType="Bearer"`、两个令牌的过期秒数及 `user: UserProfile`。

```json
{
  "accessToken": "原始访问令牌",
  "refreshToken": "原始刷新令牌",
  "tokenType": "Bearer",
  "accessTokenExpiresIn": 7200,
  "refreshTokenExpiresIn": 2592000,
  "user": {}
}
```

- **重要错误**：无效、过期或已使用的微信 code 返回 401；微信身份服务失败不得伪造本地登录。
- **幂等**：一次性 code 不作为通用重试幂等键。首次有效登录自动创建用户，同一微信身份后续登录复用同一业务用户；原始令牌只在响应中出现一次，服务端仅保存 SHA-256 摘要。

### 3.3 `POST /api/auth/refresh`

- **权限**：公开，但需有效刷新令牌，不使用 Bearer 访问令牌代替。
- **请求**：`{"refreshToken":"原始刷新令牌"}`。
- **data**：与登录相同的令牌响应及最新 `user`。
- **重要错误**：无效、过期、已撤销或已轮换的旧刷新令牌统一 401。
- **幂等**：刷新会轮换访问/刷新令牌并撤销旧会话令牌，不是可无限重复的无副作用操作；客户端应串行刷新并原子保存新令牌。

### 3.4 `POST /api/auth/logout`

- **权限**：用户。
- **请求**：无请求体，撤销当前认证会话。
- **data**：`null`。
- **重要错误**：无有效令牌 401。
- **幂等**：服务端只执行一次会话撤销；撤销后再次使用同一令牌为 401。退出不影响服务端用户、动态、评论、收藏和历史，前端应清理私人缓存。

### 3.5 `GET /api/me`

- **权限**：用户。
- **请求**：无。
- **data**：`UserProfile`，包含本人资料和完整等级进度。
- **重要错误**：401。
- **幂等**：只读，身份只从令牌取得。

### 3.6 `PATCH /api/me/profile`

- **权限**：用户。
- **请求**：支持部分修改，至少包含一个合法字段；额外传入积分、等级、角色或用户 ID 无效。

```json
{
  "displayName": "新昵称",
  "avatar": "1938572012345678999",
  "bio": "喜爱清香型茶",
  "favoriteTea": "不挑，都喝"
}
```

`avatar` 填当前用户已上传、用途为 `avatar` 且允许绑定的 `mediaId`。`displayName` trim 后非空且最多 12 个 Unicode 码点；`bio` trim 后可空且最多 24 码点；`favoriteTea` 只能是 `绿茶/白茶/黄茶/青茶/红茶/黑茶/不挑，都喝` 或空。

- **data**：更新后的 `UserProfile`。
- **重要错误**：字段越界、非法枚举 400；他人媒体、错误用途或不可绑定媒体 400/403；401。
- **幂等**：相同字段值重复 PATCH 保持相同资料；媒体绑定校验在事务内完成，不接收 wxfile 或本机路径。

### 3.7 `GET /api/me/summary`

- **权限**：用户。
- **请求**：无。
- **data**：

```json
{
  "favoriteCount": 8,
  "historyCount": 35,
  "postCount": 4,
  "points": 125,
  "level": {}
}
```

`postCount` 只统计本人可见动态；收藏计数与收藏列表约定口径一致，所有计数实时计算。

- **重要错误**：401。
- **幂等**：只读。

### 3.8 `POST /api/me/daily-points`

- **权限**：用户。
- **请求**：无业务字段；客户端日期、积分数量不生效。
- **data**：`{"businessDate":"2026-11-08","awarded":true,"points":130,"level":{}}`。
- **重要错误**：401。
- **幂等**：按 Asia/Shanghai 日期每日首次领取 5 分；`(user_id,rule_code,business_date)` 唯一。串行或并发重复请求只加一次，后续返回 `awarded=false` 和当前积分。发帖、点赞、评论及下单首版不自动加分。

### 3.9 `GET /api/me/favorites`

- **权限**：用户。
- **请求参数**：可选 `type`，以及 `pageNum/pageSize`。
- **data**：统一分页；按 `createdAt DESC,id DESC` 排序。列表项结构：

```json
{
  "id": "收藏记录 ID",
  "targetType": "tea",
  "targetId": "茶 ID",
  "available": true,
  "createdAt": "2026-11-08T12:00:00+08:00",
  "target": {}
}
```

`target` 按类型为公开茶、文章、商品或动态摘要；目标失效时 `available=false`，不返回非公开详情。

- **重要错误**：非法类型或分页参数 400；401。
- **幂等**：只读。失效记录不因下架而物理删除；`total` 与个人中心 `favoriteCount` 均统计当前筛选下所有未删除收藏，失效项以 `available=false` 保留。

### 3.10 `PUT /api/me/favorites/{type}/{id}`

- **权限**：用户。
- **路径参数**：`type=tea/article/product/post`，`id` 为目标字符串 ID。
- **请求**：无请求体。
- **data**：`{"active":true,"favoriteCount":9}`。
- **重要错误**：类型非法 400；目标不存在或当前非公开可用 404；401。
- **幂等**：`(user_id,target_type,target_id)` 唯一；重复 PUT 不增加重复记录或收藏数，取消后再次 PUT 可重新收藏仍有效的目标。

### 3.11 `DELETE /api/me/favorites/{type}/{id}`

- **权限**：用户。
- **请求**：无请求体。
- **data**：`{"active":false,"favoriteCount":8}`。
- **重要错误**：类型非法 400；401。目标已失效仍允许取消。
- **幂等**：无论记录是否存在，重复取消均保持未收藏，不影响目标内容。

### 3.12 `GET /api/me/history`

- **权限**：用户。
- **请求参数**：可选 `type`，以及 `pageNum/pageSize`。
- **data**：统一分页，按 `viewedAt DESC,id DESC` 排序；每项含 `id`、`targetType`、`targetId`、`viewedAt`、`available` 和有效时的公开 `target` 摘要。
- **重要错误**：400/401。
- **幂等**：只读；每用户全类型最多保留 50 条，失效目标仍以 `available=false` 展示。

### 3.13 `PUT /api/me/history/{type}/{id}`

- **权限**：用户。
- **请求**：无请求体；仅在对应公开详情成功展示后由客户端显式调用。
- **data**：本次历史项，含 `targetType`、`targetId`、`viewedAt`、`available`。
- **重要错误**：非法类型 400；目标非公开或不存在 404；401。
- **幂等**：同一目标去重，重复浏览只更新 `viewedAt` 并移到最前；写入与淘汰最旧记录在同一事务完成，不新增第 51 条。

### 3.14 `DELETE /api/me/history/{type}/{id}` 与 `DELETE /api/me/history`

这是两个端点，均要求用户权限、无请求体、`data=null`，均只操作本人历史。

- 单条删除：路径为 `/api/me/history/{type}/{id}`，目标不存在也返回成功。
- 清空：路径为 `/api/me/history`，清除当前用户全部类型历史。
- **重要错误**：非法类型 400；401。
- **幂等**：重复单删或清空不报错，不影响收藏、动态、评论或目标内容。

## 4. 内容、首页与百科

### 4.1 内容公共结构

`ContentBlock` 是严格校验、保序的 JSON 数组元素，不接收整段 HTML、未知块类型或未知字段：

| `type` | 必需字段 | 可选字段/约束 |
| --- | --- | --- |
| `heading` | `text`,`level` | `level` 只能为 2/3/4 |
| `paragraph` | `text` | 无 |
| `image` | `src`,`alt` | `caption` |
| `quote` | `text` | `source` |
| `list` | `items`,`ordered` | `items` 为字符串数组 |
| `steps` | `items` | 每项含 `title`,`text` |
| `tip` | `text`,`tone` | `tone=info/warning/success`，可选 `title` |

图片 `src` 必须是受控 HTTPS 地址或本系统文件 URL。

`TeaSummary` 包含 `id`、`slug`、`name`、`aliases`、`type`、`origin`、`summary`、`cover`、`featured`、`sort`。茶类固定为 `green/white/yellow/oolong/black/dark`，其中 `black` 为红茶、`dark` 为黑茶。

`TeaDetail` 在摘要基础上包含：

```json
{
  "craft": ["杀青"],
  "aromas": ["清香"],
  "flavors": ["鲜爽"],
  "suitableScenes": ["午后"],
  "brew": {
    "teaGrams": 3,
    "waterMl": 150,
    "ratio": "1:50",
    "waterTempMin": 80,
    "waterTempMax": 85,
    "vessels": ["玻璃杯"],
    "firstSteepSecondsMin": 20,
    "firstSteepSecondsMax": 30,
    "nextSteepHint": "后续适当延长时间",
    "infusionsMin": 3,
    "infusionsMax": 4,
    "methodHint": "上投法或中投法"
  },
  "storage": {
    "method": "密封低温保存",
    "shelfLifeHint": "保质与陈化提示"
  },
  "notice": "饮用提示",
  "gallery": [],
  "contentBlocks": [],
  "relatedProductIds": [],
  "status": "published",
  "createdAt": "2026-11-08T12:00:00+08:00",
  "updatedAt": "2026-11-08T12:00:00+08:00",
  "relatedTeas": []
}
```

数值必须为正且范围满足 `min <= max`。公开详情的 `relatedTeas` 从全部发布茶中排除自身，按 `sort ASC,id ASC` 取前三款。

`ArticleSummary` 包含 `id`、`slug`、`title`、`summary`、`category`、`tags`、`cover`、`relatedTeaIds`、`readMinutes`、`viewCountDisplay`、`featured`、`sort`、`publishedAt`，列表不包含 `contentBlocks`。分类固定为 `tea_basics/brewing/storage/teaware/tea_history/sensory`。

`ArticleDetail` 在摘要基础上增加 `contentBlocks`、`status`、`createdAt`、`updatedAt` 和 `relatedTeas`。`relatedTeas` 按 `relatedTeaIds` 关联发布茶，再遵循全局 `sort ASC,id ASC` 取前三款；无关联返回空数组。`viewCountDisplay` 只是内容展示值，首版无阅读量写接口。

`AppConfig` 的 `id` 固定为 `production`，包含：

```json
{
  "id": "production",
  "version": 1,
  "seasonalTheme": {
    "name": "节气主题",
    "dateRange": "内容展示日期范围",
    "recommendedAromas": [],
    "recommendedWaterTemp": "建议水温"
  },
  "banners": [],
  "featuredTeaIds": [],
  "featuredArticleIds": [],
  "announcement": "公告",
  "features": {},
  "wikiTopics": [],
  "wikiHeroArticleIds": [],
  "searchHotKeywords": []
}
```

公开配置不包含密钥或内部备注。节气日期不触发服务端自动轮换；百科专题按配置映射到冲泡、储存、茶器、茶史文章。

### 4.2 `GET /api/home`

- **权限**：公开。
- **请求**：无。
- **data**：`{"config":{},"featuredTeas":[],"featuredArticles":[]}`。
- **返回规则**：精选严格按配置 ID 数组顺序，失效引用省略；精选茶最多 4 款，精选文章保持契约。单项失效不阻塞首页。
- **重要错误**：配置不存在或服务异常按通用错误；不返回非公开内容。
- **幂等**：只读。

### 4.3 `GET /api/app-config`

- **权限**：公开。
- **请求**：无。
- **data**：`AppConfig`，含百科专题、热门搜索词和节气推荐等公开运营配置。
- **重要错误**：配置不可用时 404/500；不得泄露内部字段。
- **幂等**：只读。

### 4.4 `GET /api/teas`

- **权限**：公开。
- **请求参数**：可选 `type`、`keyword`、`featured` 和分页参数；不接受 `status` 覆盖。
- **data**：`Page<TeaSummary>`，只含发布态，按 `sort ASC,id ASC`。
- **重要错误**：非法茶类、精选标记或分页参数 400。
- **幂等**：只读，先过滤公开状态再排序分页。

### 4.5 `GET /api/teas/{id}`

- **权限**：公开。
- **请求**：路径 `id` 为茶字符串 ID。
- **data**：`TeaDetail`，含完整冲泡、储存、结构化正文和 `relatedTeas`。
- **重要错误**：不存在、草稿、下架或删除统一 404。
- **幂等**：只读，不隐式写浏览历史。

### 4.6 `GET /api/articles`

- **权限**：公开。
- **请求参数**：可选 `category`、`featured`、`keyword` 和分页参数。
- **data**：`Page<ArticleSummary>`，只含发布态，按 `sort ASC,id ASC`，不加载正文。
- **重要错误**：非法分类或分页参数 400。
- **幂等**：只读，不增加 `viewCountDisplay`。

### 4.7 `GET /api/articles/{id}`

- **权限**：公开。
- **请求**：路径 `id`。
- **data**：`ArticleDetail`，含保序内容块和 `relatedTeas`。
- **重要错误**：非发布或不存在统一 404。
- **幂等**：只读，不隐式写历史或阅读计数。

### 4.8 `GET /api/admin/teas`

- **权限**：管理员。
- **请求参数**：可选 `type`、`status`、`keyword` 和分页参数。
- **data**：`Page<TeaSummary>` 管理视图，可读取草稿、下架等状态，含 `status` 和审计时间。
- **重要错误**：401/403，非法筛选 400。
- **幂等**：只读；管理查询的可见性不能复用到公开接口。

### 4.9 `POST /api/admin/teas`

- **权限**：管理员。
- **请求**：茶创建对象，字段与 `TeaDetail` 的可写字段一致，包括唯一 `slug`、`name`、`aliases`、`type`、`origin`、`summary`、`craft`、`aromas`、`flavors`、`suitableScenes`、`brew`、`storage`、`notice`、`cover`、`gallery`、`contentBlocks`、`relatedProductIds`、`featured/sort/status`；不接收客户端审计时间。

```json
{
  "slug": "xi-hu-long-jing",
  "name": "西湖龙井",
  "aliases": [],
  "type": "green",
  "origin": {"province":"浙江省","city":"杭州市","area":"西湖区"},
  "summary": "绿茶摘要",
  "brew": {"teaGrams":3,"waterMl":150,"waterTempMin":80,"waterTempMax":85},
  "contentBlocks": [],
  "featured": true,
  "sort": 1,
  "status": "draft"
}
```

- **data**：创建后的 `TeaDetail`。
- **重要错误**：slug 冲突、非法范围、关联 ID 或内容块 400/409；401/403。
- **幂等**：未定义客户端幂等键，重复 POST 可能创建不同 ID；管理端不得盲目重复提交，slug 唯一约束防止同 slug 重入。

### 4.10 `GET /api/admin/teas/{id}`

- **权限**：管理员。
- **请求**：路径 `id`。
- **data**：管理态 `TeaDetail`，可为非公开状态。
- **重要错误**：401/403/404。
- **幂等**：只读。

### 4.11 `PUT /api/admin/teas/{id}`

- **权限**：管理员。
- **请求**：完整茶更新对象，规则同创建；路径 ID 为准，忽略或拒绝请求体中的其他 ID。
- **data**：更新后的 `TeaDetail`。
- **重要错误**：非法字段、范围、内容块或关联 400；状态冲突/slug 冲突 409；401/403/404。
- **幂等**：相同完整对象重复 PUT 保持相同业务内容；公开详情和关联解析立即遵循最新发布状态。

### 4.12 `DELETE /api/admin/teas/{id}`

- **权限**：管理员。
- **请求**：无。
- **data**：`null`。
- **重要错误**：401/403/404；历史订单或媒体引用不得被破坏。
- **幂等**：逻辑删除，重复删除不重复清理关联；删除后公开详情 404，既有动态中的关联摘要变为 `unavailable`。

### 4.13 `POST /api/admin/articles`

- **权限**：管理员。
- **请求**：唯一 `slug`、`title`、`summary`、`category`、`tags`、`cover`、`contentBlocks`、`relatedTeaIds`、`readMinutes`、`viewCountDisplay`、`featured`、`sort`；创建草稿。
- **data**：`ArticleDetail`，初始 `status=draft`。
- **重要错误**：标题、摘要、分类、正文不完整或未知内容块 400；slug 冲突 409；401/403。
- **幂等**：无创建幂等键，重复 POST 不保证合并；slug 唯一约束仍生效。

### 4.14 `GET /api/admin/articles`

- **权限**：管理员。
- **请求参数**：可选 `category`、`status`、`featured`、`keyword` 和分页参数。
- **data**：`Page<ArticleSummary>` 管理视图，可包含草稿、发布和撤回内容。
- **重要错误**：400/401/403。
- **幂等**：只读。

### 4.15 `GET /api/admin/articles/{id}`

- **权限**：管理员。
- **请求**：路径 `id`。
- **data**：管理态 `ArticleDetail`，包含正文和审计时间。
- **重要错误**：401/403/404。
- **幂等**：只读。

### 4.16 `PUT /api/admin/articles/{id}`

- **权限**：管理员。
- **请求**：完整文章更新对象，规则同创建。
- **data**：更新后的 `ArticleDetail`。
- **重要错误**：已发布文章直接编辑返回 409，必须先撤回；内容或关联非法 400；401/403/404。
- **幂等**：相同内容重复更新结果一致；`publishedAt` 等发布时间字段不能由客户端任意改写。

### 4.17 `DELETE /api/admin/articles/{id}`

- **权限**：管理员。
- **请求**：无。
- **data**：`null`。
- **重要错误**：已发布文章直接删除 409，须先撤回；401/403/404。
- **幂等**：逻辑删除，重复删除不重复处理关联；删除后公开接口 404。

### 4.18 `PUT /api/admin/articles/{id}/publication`

- **权限**：管理员。
- **请求**：无请求体，表示发布当前文章。
- **data**：发布后的 `ArticleDetail`，`status=published`，含 `publishedAt`。
- **重要错误**：不存在 404；内容完整性校验失败 400；不允许的当前状态 409；401/403。
- **幂等**：草稿或撤回文章可发布；重复发布保持首次 `publishedAt` 不变，返回当前已发布文章。

### 4.19 `DELETE /api/admin/articles/{id}/publication`

- **权限**：管理员。
- **请求**：无请求体，表示撤回发布。
- **data**：撤回后的 `ArticleDetail`，`status=withdrawn`；数据库内部对应 `offline`。
- **重要错误**：当前状态不允许撤回 409；401/403/404。
- **幂等**：重复撤回保持撤回状态，不重置首次发布时间记录；撤回后才可编辑或删除。

### 4.20 `PUT /api/admin/app-config`

- **权限**：管理员。
- **请求**：完整 `AppConfig`，`id` 必须为 `production`；包含版本、节气、Banner、精选 ID、公告、功能开关、百科专题、热门搜索词等。
- **data**：保存后的公开安全配置。
- **重要错误**：未知字段、非法内容块/URL、错误关联 ID 或版本冲突 400/409；401/403。
- **幂等**：完整替换式 PUT，相同配置重复提交结果一致；已下架或不存在的精选引用在公开聚合中省略，不阻塞服务。

## 5. 搜索、荐茶与答题

### 5.1 `GET /api/search`

- **权限**：公开。
- **请求参数**：必填 `keyword`，以及 `pageNum/pageSize`；关键词 trim 后非空。
- **data**：

```json
{
  "products": {"list":[],"total":0,"pageNum":1,"pageSize":20},
  "teas": {"list":[],"total":0,"pageNum":1,"pageSize":20},
  "articles": {"list":[],"total":0,"pageNum":1,"pageSize":20}
}
```

三组分别为在售商品、发布茶、发布文章的统一分页摘要。执行顺序为公开状态过滤、关键词匹配、`sort ASC,id ASC`、分页。搜索覆盖模块规定的茶、文章、商品及 SKU/详情块字段；中文分类同义词参与匹配，“乌龙茶”可命中 `oolong`。先完整子串匹配，未命中再按相邻二字片段任一片段匹配，不返回伪造相关度分。

- **重要错误**：空白关键词、分页非法 400；无结果返回三个空组而非错误。
- **幂等**：只读；最近搜索最多 5 条由前端维护，热门词来自 `AppConfig.searchHotKeywords`。

### 5.2 `GET /api/quiz`

- **权限**：公开。
- **请求**：无。
- **data**：

```json
{
  "quizVersion": "当前启用版本",
  "questions": [
    {
      "key": "taste",
      "title": "题目",
      "options": [{"value":"fresh","label":"选项文案"}]
    }
  ]
}
```

问卷固定 15 题，key 依次为 `taste, aroma, texture, aftertaste, strength, time, scene, effort, vessel, temp, season, pairing, explore, experience, focus`。每题单选且必答，key 和题内选项 value 唯一，数组顺序即答题顺序；不下发评分权重。

- **重要错误**：无启用问卷时 404/409。
- **幂等**：只读。

### 5.3 `POST /api/recommendations`

- **权限**：公开。
- **请求**：

```json
{
  "quizVersion": "当前启用版本",
  "answers": {
    "taste": "fresh",
    "aroma": "floral",
    "texture": "crisp",
    "aftertaste": "clean",
    "strength": "light",
    "time": "morning",
    "scene": "work",
    "effort": "quick",
    "vessel": "glass",
    "temp": "cold",
    "season": "spring",
    "pairing": "snack",
    "explore": "classic",
    "experience": "new",
    "focus": "leaf"
  }
}
```

- **data**：

```json
{
  "resultId": "不可预测的高强度结果 ID",
  "quizVersion": "当前启用版本",
  "answers": {},
  "createdAt": "2026-11-08T12:00:00+08:00",
  "recommendations": [
    {
      "tea": {},
      "score": 39,
      "traitScore": 6,
      "matchPercent": 98,
      "reasons": ["理由一","理由二","理由三"]
    }
  ]
}
```

全部发布茶参与评分，主分满分 39，六维特征分合计 0～6，仅用于同主分排序。按 `score DESC → traitScore DESC → sort ASC → id ASC` 取 Top 3；`matchPercent=min(98,62+round(score/39*36))`，不是统计概率。理由按评分规则顺序，最多三条，不足时按模块规则以香气、产地省份、建议水温补齐。

- **重要错误**：版本不存在、缺题、额外 key、未知选项或答案结构错误均 400。
- **幂等**：同版本同答案计算结果稳定；服务端可做短期幂等并复用结果，但这不是跨时间永久同一 `resultId` 的承诺。结果与登录用户无关，不建立用户画像。

### 5.4 `GET /api/recommendations/{resultId}`

- **权限**：公开。
- **请求**：路径为不可预测 `resultId`。
- **data**：与推荐创建相同的结果结构，读取时关联当前发布茶；已下线茶从结果视图过滤，原评分记录不被篡改。
- **重要错误**：不存在或不可预测 ID 不匹配 404。
- **幂等**：只读；不提供推荐结果列表。

## 6. 社区动态与互动

### 6.1 数据结构

`PostAuthor` 为实时公开资料摘要：`id`、`displayName`、`avatar`、`level`。`PostSummary`/`PostDetail` 使用以下字段：

```json
{
  "id": "动态 ID",
  "author": {"id":"用户 ID","displayName":"茶友","avatar":"https://...","level":{}},
  "content": "正文",
  "topic": "tasting",
  "tags": [],
  "images": [{"mediaId":"媒体 ID","url":"https://..."}],
  "video": null,
  "relatedTea": {"available":true,"tea":{}},
  "likeCount": 3,
  "commentCount": 2,
  "liked": false,
  "status": "published",
  "publishedAt": "2026-11-08T12:00:00+08:00",
  "createdAt": "2026-11-08T12:00:00+08:00"
}
```

`topic` 发布枚举为 `tasting/brewing/teaware/daily/question`；`all` 仅用于查询筛选。公开状态只有 `published` 可见；`hidden/deleted` 对他人不可见。茶下架后动态仍可展示，但 `relatedTea.available=false`，不返回非公开茶详情。游客视图中 `liked=false`。

`Comment` 包含 `id`、`postId`、`author`、`content`、`createdAt`；首版无回复、楼中楼和评论点赞。

### 6.2 `GET /api/posts`

- **权限**：公开，可带有效令牌取得本人 `liked`。
- **请求参数**：可选 `topic`（合法话题或 `all`）和分页参数。
- **data**：`Page<PostSummary>`，仅发布态，按 `publishedAt DESC,id DESC`。
- **重要错误**：非法话题/分页 400；显式无效令牌 401。
- **幂等**：只读，不使用“本机动态置顶”等演示排序。

### 6.3 `GET /api/posts/{id}`

- **权限**：公开，可带有效令牌。
- **请求**：路径 `id`。
- **data**：`PostDetail`，只带少量评论预览；完整评论必须调用评论分页接口。
- **重要错误**：不存在、隐藏、删除均对他人 404；无效令牌 401。
- **幂等**：只读。

### 6.4 `POST /api/posts`

- **权限**：用户。
- **请求**：

```json
{
  "clientRequestId": "客户端生成的请求 ID",
  "content": "今天的茶感记录",
  "topic": "tasting",
  "tags": ["清香"],
  "imageMediaIds": ["媒体 ID 1","媒体 ID 2"],
  "videoMediaId": null,
  "relatedTeaId": "茶 ID 或 null"
}
```

正文 trim 后非空、最多 300 个 Unicode 码点，允许纯文字；`tags` 最多 5 个，每项 trim 后非空并满足服务端限长。图片 0～9 张，或 1 个视频，二者互斥。媒体必须属于当前用户、`purpose=post`、`UPLOADED` 且未绑定；关联茶非空时必须公开发布。服务端不接受作者、状态、发布时间或互动计数。

- **data**：创建后的 `PostDetail`，状态 `published`。
- **重要错误**：正文/标签/话题/媒体数量或关联茶非法 400；他人媒体 403；资源不存在 404；同键不同内容 409。
- **幂等**：`(author_id,client_request_id)` 唯一；同键同规范化请求返回首次动态。发布与媒体绑定同事务，失败不留下半条动态或半绑定媒体。

### 6.5 `GET /api/me/posts`

- **权限**：用户。
- **请求参数**：分页参数。
- **data**：`Page<PostSummary>`，只含本人 `published/hidden` 内容，不含 `deleted`，按 `publishedAt DESC,id DESC`。
- **重要错误**：400/401。
- **幂等**：只读，严格按认证身份隔离。

### 6.6 `DELETE /api/posts/{id}`

- **权限**：用户，且必须是动态作者。
- **请求**：无。
- **data**：`null`。
- **重要错误**：非作者 403；不存在 404；401。
- **幂等**：逻辑删除并置 `status=deleted`，重复删除成功且不重复清理。删除后公共详情和评论 404，不再允许点赞/评论；历史互动保留但不公开。

### 6.7 `PUT /api/posts/{id}/like`

- **权限**：用户。
- **请求**：无。
- **data**：`{"liked":true,"likeCount":4}`，为权威值。
- **重要错误**：动态非发布、隐藏或删除 404/409；401。
- **幂等**：`(post_id,user_id)` 唯一，点赞行与 `likeCount` 同事务；重复或并发 PUT 只计一次。

### 6.8 `DELETE /api/posts/{id}/like`

- **权限**：用户。
- **请求**：无。
- **data**：`{"liked":false,"likeCount":3}`。
- **重要错误**：动态不可见或不可互动 404/409；401。
- **幂等**：重复取消不重复扣减，计数不得小于 0；不是 toggle。

### 6.9 `GET /api/posts/{id}/comments`

- **权限**：公开。
- **请求参数**：分页参数。
- **data**：`Page<Comment>`，动态须公开可见，按 `createdAt ASC,id ASC`。
- **重要错误**：动态隐藏、删除或不存在 404；分页非法 400。
- **幂等**：只读；删除动态后评论记录可保留，但此接口不可访问。

### 6.10 `POST /api/posts/{id}/comments`

- **权限**：用户。
- **请求**：`{"clientRequestId":"请求 ID","content":"评论内容"}`。正文 trim 后非空、最多 200 个 Unicode 码点。
- **data**：`{"comment":{},"commentCount":1}`，包含创建的 `Comment` 和权威动态评论数。
- **重要错误**：正文非法 400；动态不可见/不可评论 404/409；同键不同内容 409；401。
- **幂等**：`(author_id,client_request_id)` 唯一，同键同请求返回原评论；评论行与动态 `commentCount` 同事务，只增加一次。

### 6.11 `DELETE /api/posts/{postId}/comments/{commentId}`

- **权限**：用户，且必须是评论作者；动态作者无权据此删除他人评论。
- **请求**：无。
- **data**：`{"deleted":true,"commentCount":1}`，返回权威动态评论数。
- **重要错误**：非评论作者 403；动态/评论不存在或归属路径不匹配 404；401。
- **幂等**：软删除并公开隐藏，首次删除只减一次计数，重复删除不重复扣减。

### 6.12 `PUT /api/admin/posts/{id}/visibility`（可选管理能力）

- **权限**：管理员。
- **请求**：`{"status":"hidden"}` 表示隐藏，`{"status":"published"}` 表示恢复发布；不接受 `deleted`，不引入审核状态。
- **data**：`{"id":"动态 ID","status":"hidden"}`。
- **重要错误**：非法状态 400；已删除动态不能通过此接口恢复，返回 409；401/403/404。
- **幂等**：重复设置同一状态结果一致。隐藏/恢复不能复用作者删除逻辑；隐藏后公开详情、评论不可见且禁止互动，我的动态仍对作者可见。

## 7. 媒体文件

### 7.1 `POST /api/media`

- **权限**：用户；`purpose=content/product` 还必须是管理员。
- **请求**：`multipart/form-data`，字段为 `file` 和 `purpose`。`purpose` 支持 `avatar/post/content/product`；普通用户只能上传 `avatar/post`。
- **data**：

```json
{
  "mediaId": "媒体 ID",
  "url": "https://cdn.example.com/media/generated-key",
  "mediaType": "image",
  "mimeType": "image/webp",
  "sizeBytes": 102400,
  "width": 800,
  "height": 600,
  "durationSeconds": null,
  "createdAt": "2026-11-08T12:00:00+08:00"
}
```

视频使用 `mediaType=video`，可含 `durationSeconds`，宽高/时长不适用时为 `null`。对象名由服务端随机生成，不使用原始文件名做路径。

- **重要错误**：未登录 401；普通用户上传 `content/product` 403；伪造扩展名、MIME、文件头、损坏文件或视频时长无法读取 400；图片超过 5 MiB、视频超过 20 MiB或请求超限 413；生产存储不可用 503。
- **类型限制**：图片仅 JPEG/PNG/WebP，视频仅 MP4 且首版最长 60 秒；拒绝 HTML、SVG、脚本和可执行内容。
- **幂等**：上传不是按文件名覆盖的幂等操作，重复上传可生成不同 mediaId；不得覆盖其他对象。元数据与对象写入失败需补偿清理，失败响应不产生可引用媒体。

### 7.2 `GET /api/media/{id}/content`

- **权限**：公开的受控媒体读取，不使用 Bearer 权限代替业务可见性校验。
- **请求**：路径 `id`；也可由上传响应中的公开 CDN URL 访问。
- **响应**：LOCAL/S3/COS 成功返回文件二进制及正确 `Content-Type`、`Content-Length`，不是 CommonResponse；受控种子 `EXTERNAL` 媒体返回 302 到原 HTTPS 地址。失败仍使用统一错误响应。
- **重要错误**：媒体不存在、删除或不可公开访问 404；存储不可用 503。
- **幂等**：只读。私有下载和签名 URL 不属于首版；删除业务内容不立即物理删除媒体，孤儿清理由独立维护任务执行。

## 8. 商城与订单

### 8.1 商品结构

`ProductSummary` 包含 `id`、`code`、`categoryId`、`primaryTeaId`、`relatedTeaIds`、`name`、`subtitle`、`coverUrl`、`galleryUrls`、`tags`、`featured`、`sort`、`status`，以及默认可用规格的价格展示字段 `priceFen/originalPriceFen/defaultSkuId`。

`Sku`：

```json
{
  "id": "SKU ID",
  "name": "规格名称",
  "skuCode": "SKU 编码",
  "priceFen": 19900,
  "originalPriceFen": 23900,
  "enabled": true,
  "default": true,
  "stockOnHand": 20,
  "stockReserved": 3,
  "availableStock": 17,
  "version": 1
}
```

金额满足 `originalPriceFen >= priceFen >= 0`，库存不为负。`ProductDetail` 在商品摘要上增加 `blocks` 和 `skus`；公开详情只暴露启用规格，列表使用默认可用规格价格。商品状态使用 `DRAFT/ON_SALE/OFF_SHELF`，仅 `ON_SALE` 公开。

### 8.2 `GET /api/products`

- **权限**：公开。
- **请求参数**：可选 `categoryId`、`teaId`、`keyword`、`featured` 和分页参数。
- **data**：`Page<ProductSummary>`，只在售商品，按 `sort ASC,id ASC` 后分页。
- **重要错误**：非法筛选或分页 400。
- **幂等**：只读；搜索匹配可包含名称、副标题、标签、SKU 和详情块，但不得泄露下架内容。

### 8.3 `GET /api/products/{id}`

- **权限**：公开。
- **请求**：路径 `id`。
- **data**：`ProductDetail`，含启用规格、结构化详情和当前可用库存。
- **重要错误**：下架、草稿、删除或不存在统一 404。
- **幂等**：只读，不锁库存，不隐式写历史。

### 8.4 `POST /api/admin/products`

- **权限**：管理员。
- **请求**：商品可写字段及一对多规格；详情块遵循 `ContentBlock`。规格创建示例：

```json
{
  "code": "PRODUCT-CODE",
  "categoryId": "分类 ID",
  "primaryTeaId": "茶 ID",
  "relatedTeaIds": ["茶 ID"],
  "name": "商品名称",
  "subtitle": "副标题",
  "coverUrl": "https://...",
  "galleryUrls": [],
  "tags": [],
  "blocks": [],
  "featured": false,
  "sort": 1,
  "status": "DRAFT",
  "skus": [
    {
      "name": "100g",
      "skuCode": "SKU-100G",
      "priceFen": 19900,
      "originalPriceFen": 23900,
      "enabled": true,
      "default": true,
      "stockOnHand": 20
    }
  ]
}
```

- **data**：创建后的 `ProductDetail`。
- **重要错误**：价格、库存、默认规格、关联茶或内容块非法 400；商品编码/SKU 编码冲突 409；401/403。
- **幂等**：未定义创建幂等键，重复 POST 不保证合并；编码唯一约束防止重复编码。`stockReserved` 和版本由服务端管理。

### 8.5 `PUT /api/admin/products/{id}`

- **权限**：管理员。
- **请求**：路径 `id` 指定待更新商品；请求体为完整商品对象，不在 body 指定商品 ID。既有 SKU 带 `id`，其他字段同创建。不得由客户端直接篡改预占库存。
- **data**：更新后的 `ProductDetail`。
- **重要错误**：商品或 SKU 不存在 404；非法价格库存/内容块 400；编码或版本状态冲突 409；401/403。
- **幂等**：相同完整对象重复 PUT 保持一致；商品后续修改不改变历史订单快照，也不能使已预占库存为负。

### 8.6 `PUT /api/admin/products/{id}/status`

- **权限**：管理员。
- **请求**：`{"status":"ON_SALE"}` 表示上架在售，`{"status":"OFF_SHELF"}` 表示下架；不能借该接口物理删除。
- **data**：更新后的 `ProductDetail`。
- **重要错误**：非法状态/缺少可售规格 400；状态冲突 409；401/403/404。
- **幂等**：重复设置同状态不重复处理库存；下架后公开详情 404，购物车保留该项并标记失效，历史订单快照不变。

### 8.7 购物车数据结构

`Cart`：

```json
{
  "items": [
    {
      "id": "购物车行 ID",
      "productId": "商品 ID",
      "skuId": "SKU ID",
      "productName": "当前商品名",
      "skuName": "当前规格名",
      "coverUrl": "https://...",
      "priceFen": 19900,
      "quantity": 2,
      "checked": true,
      "subtotalFen": 39800,
      "availableStock": 17,
      "invalidReason": null
    }
  ],
  "checkedCount": 2,
  "totalFen": 39800,
  "cartCount": 2
}
```

每次读取按当前 SKU 重算单价、小计与勾选总额。下架、停用、无库存等行不静默消失，`invalidReason` 给出原因且不能结算。购物车不锁库存。

### 8.8 `GET /api/cart`

- **权限**：用户。
- **请求**：无。
- **data**：最新 `Cart`。
- **重要错误**：401。
- **幂等**：只读，仅重新计算展示值，不预占库存。

### 8.9 `POST /api/cart/items`

- **权限**：用户。
- **请求**：`{"productId":"商品 ID","skuId":"SKU ID","quantity":2}`；数量 1～99。
- **data**：追加数量并自动勾选后的最新 `Cart`。同一用户、商品、规格合并为唯一行。
- **重要错误**：商品/SKU 不存在或不可售 404/409；数量范围非法 400；超过可用库存 409；401。
- **幂等**：这是“追加数量”而非幂等 PUT，未定义 `clientRequestId`；客户端不得在网络结果未知时盲目重复加购，以免数量再次增加。

### 8.10 `PATCH /api/cart/items/{id}`

- **权限**：用户，仅本人购物车行。
- **请求**：支持部分修改，至少含 `quantity` 或 `checked`：`{"quantity":3,"checked":true}`。数量 1～99。
- **data**：最新 `Cart`。
- **重要错误**：行不存在或属于他人 404；非法数量 400；超库存 409；401。
- **幂等**：相同目标数量/勾选状态重复 PATCH 结果一致，不累加数量。

### 8.11 `DELETE /api/cart/items/{id}`

- **权限**：用户，仅本人购物车行。
- **请求**：无。
- **data**：移除后的最新 `Cart`。
- **重要错误**：他人或不存在的行按资源隐藏返回 404；401。
- **幂等**：删除最终状态为该行不存在；实现可对本人重复删除返回当前购物车，不重复调整任何计数或库存。

### 8.12 `PUT /api/cart/selection`

- **权限**：用户。
- **请求**：`{"checked":true}` 全选，`{"checked":false}` 全不选。
- **data**：最新 `Cart`，失效行仍保留并带原因。
- **重要错误**：缺少或非法 `checked` 400；401。
- **幂等**：重复设置相同全选状态结果一致，不改变数量、不锁库存。

### 8.13 `DELETE /api/cart`

- **权限**：用户。
- **请求**：无。
- **data**：清空后的 `Cart`，通常为 `items=[]` 且各计数、总额为 0。
- **重要错误**：401。
- **幂等**：重复清空结果一致，不影响商品库存、收藏或历史订单。

### 8.14 报价与订单结构

`CheckoutQuote`：

```json
{
  "quoteId": "报价 ID",
  "expiresAt": "2026-11-08T12:05:00+08:00",
  "items": [
    {
      "cartItemId": "购物车行 ID",
      "productId": "商品 ID",
      "skuId": "SKU ID",
      "quantity": 2,
      "priceFen": 19900,
      "subtotalFen": 39800
    }
  ],
  "productTotalFen": 39800,
  "deliveryFeeFen": 0,
  "discountFen": 0,
  "payableFen": 39800,
  "currency": "CNY"
}
```

`Order` 包含 `orderNo`、`status`、`paymentStatus`、`fulfillmentStatus`、`paymentMode`、订单项快照、金额、`pickup`、`remark` 和审计时间。订单项快照至少含 `productId`、`skuId`、`productName`、`skuName`、`coverUrl`、`priceFen`、`quantity`、`subtotalFen`，不随商品后续修改变化。

订单状态：`PENDING_PAYMENT/PAID/CANCELLED/CLOSED`；支付状态：`UNPAID/PAYING/PAID`；自提履约状态：`NOT_STARTED/PENDING_PICKUP/COMPLETED`。新订单三者初始为 `PENDING_PAYMENT/UNPAID/NOT_STARTED`。

### 8.15 `POST /api/checkout/quote`

- **权限**：用户。
- **请求**：只传购物车行 ID 和自提信息，不传商品名、单价、小计或总额。`pickup` 仅允许可选的 `storeId`，也可传空对象 `{}`；不采集联系人或电话。

```json
{
  "cartItemIds": ["购物车行 ID"],
  "pickup": {
    "storeId": "自提点 ID"
  }
}
```

- **data**：`CheckoutQuote`，服务端重新校验公开状态、规格、数量、库存并计价。
- **重要错误**：空选中项、行不属于本人、含失效行 400/404；库存不足 409；401。
- **幂等**：不创建订单、不预占库存，但报价有过期时间；价格或库存变化后相同请求可得到新报价，创建订单时必须再次校验。

### 8.16 `POST /api/orders`

- **权限**：用户。
- **请求**：`pickup` 规则同报价，仅需可选 `storeId` 或空对象，不接收联系人或电话。

```json
{
  "clientRequestId": "客户端请求 ID",
  "quoteId": "报价 ID",
  "cartItemIds": ["购物车行 ID"],
  "pickup": {
    "storeId": "自提点 ID"
  },
  "remark": "备注"
}
```

不接受客户端金额或商品快照。创建时在单事务内再次校验报价、商品、规格、数量和库存，创建订单及快照、原子预占库存，并只扣除购物车本次成交数量；下单期间新增数量不得误删。首版下单不增加积分。

- **data**：完整 `Order`。
- **重要错误**：空项或自提信息非法 400；报价过期/价格变化 `QUOTE_EXPIRED/QUOTE_CHANGED` 409；库存不足 409；同键不同规范化请求 `IDEMPOTENCY_CONFLICT` 409；401。
- **幂等**：`(user_id,client_request_id)` 唯一。请求指纹基于规范化购物车行/数量、自提和备注，不含客户端金额；同键同请求返回首次订单，串行或并发均只创建一单。异常响应后必须用同键同内容重试。

### 8.17 `GET /api/orders`

- **权限**：用户。
- **请求参数**：可选 `status` 和分页参数。
- **data**：`Page<Order>` 或不含完整快照扩展字段的订单摘要分页，每项至少含 `orderNo`、三个状态、金额和创建时间；只返回本人订单。
- **重要错误**：非法状态/分页 400；401。
- **幂等**：只读。

### 8.18 `GET /api/orders/{orderNo}`

- **权限**：用户，仅本人订单。
- **请求**：路径 `orderNo`。
- **data**：完整 `Order`，含商品/规格历史快照、自提信息和支付模式。
- **重要错误**：他人订单与不存在订单均 404；401。
- **幂等**：只读。

### 8.19 `POST /api/orders/{orderNo}/cancel`

- **权限**：用户，仅本人订单。
- **请求**：无。
- **data**：取消后的 `Order`，状态 `CANCELLED`。
- **重要错误**：他人订单 404；非 `PENDING_PAYMENT`（包括已支付、已关闭）409；401。首版已支付订单不可由用户取消。
- **幂等**：取消只发生一次并只释放一次预占库存；重复取消返回当前已取消订单，不重复释放。

### 8.20 `POST /api/orders/{orderNo}/payments`

- **权限**：用户，仅本人订单。
- **请求**：`{"mode":"DEMO"}` 仅用于显式开启演示支付的开发环境。真实渠道模式只有在服务端真实微信支付适配器配置完成后才可用，本接口不承诺首版已提供真实扣款。
- **data**：

```json
{
  "orderNo": "订单号",
  "paymentMode": "DEMO",
  "channel": "DEMO",
  "paymentStatus": "PAID",
  "orderStatus": "PAID",
  "fulfillmentStatus": "PENDING_PICKUP",
  "serverPaymentNo": "服务端支付号",
  "thirdPartyTransactionNo": null,
  "paidAt": "2026-11-08T12:00:00+08:00",
  "demoNotice": "演示订单，不产生真实扣款"
}
```

真实模式不得返回 `demoNotice`，且订单状态只能由验签通知或主动查单推进，不能由小程序 success 回调推进。

- **重要错误**：他人/不存在订单 404；订单非待支付或已关闭 409；生产 DEMO 关闭或真实支付未配置时 503 `PAYMENT_UNAVAILABLE`，不得自动降级；401。
- **幂等**：支付单与订单分离，订单号、服务端支付号、第三方交易号均有唯一约束。已支付重复请求返回当前支付结果，不重复扣款/扣库存；`PAYING` 重试应查询或复用当前支付单，失败后才允许重新发起。

### 8.21 `GET /api/orders/{orderNo}/payment-result`

- **权限**：用户，仅本人订单。
- **请求**：无。
- **data**：`{"orderNo":"订单号","orderStatus":"PENDING_PAYMENT","paymentStatus":"UNPAID","paymentMode":"DEMO","paidAt":null}`；真实支付以服务端查单或已验签通知结果为准。
- **重要错误**：他人订单 404；真实渠道查单不可用可返回 503；401。
- **幂等**：只读，可轮询刷新权威状态，但不能凭客户端传入“成功”修改订单。

### 8.22 `POST /api/payments/wechat/notify`（预留，非首版已实现）

- **权限**：预留的微信支付服务端通知入口，不使用用户 Bearer token；启用时必须验证微信平台签名、证书、商户、订单号和金额。
- **请求**：未来按微信支付通知协议接收原始通知体；当前未接入时返回 503 `PAYMENT_UNAVAILABLE`，不得用模拟 JSON 通知把订单改为已支付。
- **响应**：首版无可用业务 `data`。未来启用后按微信支付通知协议应答，不能以普通客户端“支付成功”请求替代。
- **重要错误**：伪造签名、证书/商户不符、金额不符均拒绝；重复或乱序通知不得使状态倒退。
- **幂等**：第三方交易号唯一；重复通知只确认当前结果，不重复扣库存、释放库存或重复推进状态。真实资金验收依赖商户号、证书和回调配置，未具备时属于未验收能力。

### 8.23 `POST /api/admin/orders/{orderNo}/pickup-completion`

- **权限**：管理员。
- **请求**：无业务金额或状态字段，表示对当前订单完成到店自提核销。
- **data**：更新后的 `Order`，支付订单的 `fulfillmentStatus=COMPLETED`。
- **重要错误**：订单不存在 404；未支付、已取消/关闭或履约状态不允许核销 409；401/403。
- **幂等**：重复核销保持 `COMPLETED`，不重复变更库存、支付或积分；只有已支付订单可从 `PENDING_PICKUP` 核销为 `COMPLETED`。
