# 茶一统 MySQL 8 数据库设计

## 1. 设计基线

本文覆盖微信认证、用户资产、内容与推荐、媒体、社区和商城交易，数据库使用 MySQL 8.x。表结构通过版本化迁移交付，应用启动不自动建表；本文只定义逻辑模型，不代替迁移脚本。

### 1.1 通用列与数据类型

除特别说明外，每张表均包含以下四列，对应模板的 `BaseEntity`：

| 列 | 类型 | 空值 | 默认 | 含义 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 否 | 无 | 内部主键，应用生成雪花 ID，Java 使用 `Long`，不使用自增 |
| `created_at` | `DATETIME(6)` | 否 | 无 | 创建时间，由应用填充 |
| `updated_at` | `DATETIME(6)` | 否 | 无 | 最近修改时间，由应用填充 |
| `deleted` | `INT` | 否 | `0` | 逻辑删除标记，`0` 正常、`1` 删除，由逻辑删除处理器维护 |

统一规则：

- 引擎使用 InnoDB，字符集使用 `utf8mb4`；业务标识、摘要哈希和交易号使用 ASCII 二进制排序规则，避免大小写或尾部空格造成误判。
- 所有 `DATETIME(6)` 均保存 UTC。应用以 UTC 写入，数据库连接时区固定为 UTC，不使用服务器本地时间；API 输出带 `Z` 或偏移量的 ISO 8601 字符串。只有每日积分的 `business_date` 是按 `Asia/Shanghai` 计算的纯业务日期。
- 金额列均为 `BIGINT`，单位为分，币种为 CNY；禁止使用浮点数。库存、数量和积分使用整数。
- `JSON` 列由应用写入合法 JSON；数组顺序就是展示顺序。有序内容块、答案、理由、配置和必要快照使用 JSON，不把大段 HTML 入库。应用负责严格校验块类型、字段、长度和数组顺序。
- 业务删除均为逻辑删除，默认查询必须带 `deleted=0`。唯一键中的业务标识永久不复用；收藏、点赞、历史等可反向操作的数据采用“恢复原行”，不通过往唯一键拼接 `deleted` 制造重复墓碑行。
- 普通单体关联可建立物理外键，统一 `ON DELETE RESTRICT`，不依赖级联删除或级联更新计数。JSON 内 ID、跨类型目标以及媒体通用绑定均为逻辑关联，由服务层校验，不声明无法成立的 polymorphic 外键。
- 对外接口统一使用 `/api` 前缀；成功响应的业务码固定为字符串 `00000`，与 HTTP 200/201 配合使用。本文出现的 401/403/404/409 等均为失败 HTTP 状态，不是成功业务码。

### 1.2 内外 ID 与公开可见性

- 茶、文章、商品、商品分类、SKU、帖子、评论和用户同时具有内部 `id` 与不可变 `business_key`。前端路由、收藏、历史、配置引用和 JSON 关联使用字符串 `business_key`；模块内部 join 使用 `BIGINT id`。
- `slug` 是可独立使用的唯一公开别名，不替代主键，不因改名而复用。`result_id`、`media_id`、`order_no`、`payment_no` 分别承担对应资源的公开标识。
- `recommendation_result.result_id` 使用至少 128 位密码学随机数的 Base64URL 编码，不使用可枚举雪花 ID；推荐结果没有列表接口。
- API 中确需暴露的 `Long`（例如购物车行 ID）序列化为十进制字符串，避免 JavaScript 精度丢失；数据库仍使用 `BIGINT`。
- 公开查询始终同时过滤逻辑删除与公开状态。茶/文章为 `published`，商品为 `ON_SALE`，帖子为 `published`；下架内容保留供快照或私有记录解析，但不得泄露非公开详情。

## 2. 表级清单

| 分组 | 表名 | 用途 | 逻辑删除 |
| --- | --- | --- | --- |
| 用户 | `users` | 用户资料、角色、积分余额缓存 | 是；注销不物理清除交易记录 |
| 用户 | `wechat_identity` | 微信 appid+openid 与内部用户的唯一映射 | 是 |
| 用户 | `auth_session` | 访问/刷新令牌摘要、过期、轮换与撤销 | 是；撤销优先用 `revoked_at` |
| 用户 | `point_ledger` | 积分流水与每日领取幂等 | 是；正常业务不删除流水 |
| 个人资产 | `favorite` | 四类目标的收藏 | 是；取消置删，再收藏恢复原行 |
| 个人资产 | `browsing_history` | 去重足迹及每用户 50 条上限 | 是；删除/清空置删，再浏览可恢复 |
| 媒体 | `media_file` | 对象存储元数据、归属与绑定 | 是；对象清理由独立维护任务执行 |
| 内容 | `tea` | 茶百科与冲泡、储存、正文 | 是 |
| 内容 | `article` | 文章与发布生命周期 | 是 |
| 内容 | `app_config` | 首页、百科、热门词、功能开关等公开配置 | 是 |
| 推荐 | `quiz_definition` | 版本化问卷与私有评分规则 | 是 |
| 推荐 | `recommendation_result` | 一次推荐输入和公开随机结果 ID | 是；正常业务不删除 |
| 推荐 | `recommendation_item` | 每款茶的评分、排序与理由 | 是；随结果保留 |
| 社区 | `post` | 帖子正文、状态、作者与权威计数 | 是；作者删除同时改状态 |
| 社区 | `post_media` | 帖子图片/视频及顺序 | 是；随帖子保留绑定历史 |
| 社区 | `post_like` | 用户点赞关系 | 是；取消置删，再点赞恢复 |
| 社区 | `post_comment` | 评论与发布幂等 | 是；作者删除置删 |
| 商城 | `product_category` | 商品分类 | 是 |
| 商城 | `product` | 商品内容、上下架与关联 | 是 |
| 商城 | `product_sku` | SKU 价格、库存、预占和版本 | 是；有订单引用时仅停用/逻辑删除 |
| 商城 | `cart_item` | 本人购物车行 | 是；移除置删，再加购恢复 |
| 交易 | `shop_order` | 订单金额、自提信息、状态机与下单幂等 | 是；不提供普通用户删除 |
| 交易 | `order_item` | 成交商品/SKU 不可变快照 | 是；正常业务不删除 |
| 交易 | `payment_transaction` | 独立支付尝试、渠道交易号与回调状态 | 是；正常业务不删除 |

## 3. 用户认证与个人资产

### 3.1 `users`：用户

| 关键列 | 类型 | 空值 | 默认 | 含义 |
| --- | --- | --- | --- | --- |
| `business_key` | `VARCHAR(64)` | 否 | 无 | 对外稳定用户 ID，不作为微信标识 |
| `display_name` | `VARCHAR(48)` | 否 | 无 | 昵称；应用按 Unicode 码点校验 1～12 |
| `avatar_media_id` | `BIGINT` | 是 | `NULL` | 当前头像媒体内部 ID |
| `bio` | `VARCHAR(100)` | 是 | `NULL` | 签名，最多 24 码点 |
| `favorite_tea` | `VARCHAR(32)` | 是 | `NULL` | 常喝茶固定枚举或空 |
| `role` | `VARCHAR(16)` | 否 | `'USER'` | `USER/ADMIN`，只能由受控管理变更设置 |
| `points_balance` | `BIGINT` | 否 | `0` | 当前积分余额缓存，与积分流水同事务更新 |
| `status` | `VARCHAR(16)` | 否 | `'ACTIVE'` | 账号状态，禁用账号不能建立新会话 |

- **主键/唯一约束**：主键 `id`；`uk_users_business_key(business_key)`。
- **索引**：`idx_users_role_status(role,status,id)` 供管理查询。
- **逻辑删除**：`deleted=1` 后不可登录；历史帖子和订单仍按业务可见性处理。
- **关联/一致性**：`avatar_media_id` 逻辑关联 `media_file.id`，避免用户与媒体建表循环；更新头像须在同一事务校验媒体属于本人、用途为 `avatar`、状态可绑定，并绑定新媒体、解除旧头像绑定。等级完全由 `points_balance` 按九级门槛实时计算，不冗余存储。

### 3.2 `wechat_identity`：微信身份

| 关键列 | 类型 | 空值 | 默认 | 含义 |
| --- | --- | --- | --- | --- |
| `user_id` | `BIGINT` | 否 | 无 | 内部用户 ID |
| `appid` | `VARCHAR(64)` | 否 | 无 | 微信小程序 appid |
| `openid` | `VARCHAR(128)` | 否 | 无 | 微信身份标识，仅服务端保存 |
| `unionid` | `VARCHAR(128)` | 是 | `NULL` | 可选 unionid，不参与首版登录主映射 |
| `last_login_at` | `DATETIME(6)` | 是 | `NULL` | 最近成功登录时间 |

- **主键/唯一约束**：主键 `id`；`uk_wechat_appid_openid(appid,openid)`，保证同一微信身份只映射一个用户。
- **索引**：`idx_wechat_user(user_id)`。
- **逻辑删除**：禁用映射用 `deleted=1`，但不得把同一 openid 改绑给其他用户；安全改绑需受控迁移。
- **关联/一致性**：`user_id` 物理关联 `users.id`。首次登录在事务内创建用户与身份；并发遇到唯一键冲突后读取已存在身份。`session_key`、AppSecret 和原始微信 code 均不入库。

### 3.3 `auth_session`：令牌会话

| 关键列 | 类型 | 空值 | 默认 | 含义 |
| --- | --- | --- | --- | --- |
| `user_id` | `BIGINT` | 否 | 无 | 会话所属用户 |
| `access_token_hash` | `CHAR(64)` | 否 | 无 | 当前访问令牌 SHA-256 小写十六进制摘要 |
| `refresh_token_hash` | `CHAR(64)` | 否 | 无 | 当前刷新令牌 SHA-256 摘要 |
| `access_expires_at` | `DATETIME(6)` | 否 | 无 | 访问令牌 UTC 过期时间 |
| `refresh_expires_at` | `DATETIME(6)` | 否 | 无 | 刷新令牌 UTC 过期时间 |
| `rotated_at` | `DATETIME(6)` | 是 | `NULL` | 最近刷新轮换时间 |
| `revoked_at` | `DATETIME(6)` | 是 | `NULL` | 撤销时间，非空即不可用 |

- **主键/唯一约束**：主键 `id`；访问、刷新摘要各有唯一约束，防止令牌碰撞或复用。
- **索引**：`idx_auth_session_user(user_id,revoked_at,refresh_expires_at)`。
- **逻辑删除**：退出写 `revoked_at`，不只依赖逻辑删除；过期会话可由维护任务逻辑删除。
- **关联/一致性**：`user_id` 物理关联 `users.id`。原始 opaque token 只出现在登录/刷新响应。刷新时锁定会话行，核对摘要、撤销与过期状态后，原子替换两种摘要和过期时间；旧刷新令牌立即失效，退出则撤销当前会话。

### 3.4 `point_ledger`：积分流水

| 关键列 | 类型 | 空值 | 默认 | 含义 |
| --- | --- | --- | --- | --- |
| `user_id` | `BIGINT` | 否 | 无 | 积分所属用户 |
| `rule_code` | `VARCHAR(32)` | 否 | 无 | 首版固定 `DAILY_CHECKIN` |
| `business_date` | `DATE` | 是 | `NULL` | 每日领取的 Asia/Shanghai 业务日期 |
| `delta_points` | `INT` | 否 | 无 | 本次变动，每日领取为 `5` |
| `balance_after` | `BIGINT` | 否 | 无 | 变动后的余额，便于审计 |
| `description` | `VARCHAR(128)` | 是 | `NULL` | 服务端生成的说明 |

- **主键/唯一约束**：主键 `id`；`uk_points_daily(user_id,rule_code,business_date)` 是每日领取幂等依据；非日期型未来规则需另设业务幂等键。
- **索引**：`idx_points_user_created(user_id,created_at,id)`。
- **逻辑删除**：正常积分业务不删除；冲正应新增反向流水而不是篡改历史。
- **关联/一致性**：`user_id` 物理关联 `users.id`。每日领取事务先锁用户行，再插入流水并更新 `users.points_balance`；唯一键冲突表示已领取，回滚本次重复写入并返回 `awarded=false`，余额最多增加一次。

### 3.5 `favorite`：收藏

| 关键列 | 类型 | 空值 | 默认 | 含义 |
| --- | --- | --- | --- | --- |
| `user_id` | `BIGINT` | 否 | 无 | 收藏者 |
| `target_type` | `VARCHAR(16)` | 否 | 无 | `tea/article/product/post` |
| `target_id` | `VARCHAR(64)` | 否 | 无 | 目标的不可变 `business_key` |
| `active_at` | `DATETIME(6)` | 否 | 无 | 最近收藏时间，API 的收藏 `createdAt` 取此值 |

- **主键/唯一约束**：主键 `id`；`uk_favorite_user_target(user_id,target_type,target_id)`。
- **索引**：`idx_favorite_user_active(user_id,deleted,active_at,id)` 支撑全类型稳定倒序；`idx_favorite_user_type(user_id,deleted,target_type,active_at,id)` 支撑类型筛选。
- **逻辑删除**：取消将已有行置 `deleted=1`；再次收藏恢复原行、刷新 `active_at`，因此重复 PUT/DELETE 均幂等。
- **关联/一致性**：仅 `user_id` 是物理外键。`target_type+target_id` 是**逻辑关联**，新增时按类型分别查询目标公开表，绝不声明一个直接指向四张表的外键。目标下架后收藏行保留并展示 `available=false`；收藏列表 `total` 与个人中心收藏数共用“未删除记录”的统计口径，失效占位项仍计入；目标是否公开仅影响摘要展示。

### 3.6 `browsing_history`：浏览历史

| 关键列 | 类型 | 空值 | 默认 | 含义 |
| --- | --- | --- | --- | --- |
| `user_id` | `BIGINT` | 否 | 无 | 浏览者 |
| `target_type` | `VARCHAR(16)` | 否 | 无 | 与收藏相同的四类目标 |
| `target_id` | `VARCHAR(64)` | 否 | 无 | 目标不可变 `business_key` |
| `viewed_at` | `DATETIME(6)` | 否 | 无 | 最近浏览时间，重复浏览只更新此列 |

- **主键/唯一约束**：主键 `id`；`uk_history_user_target(user_id,target_type,target_id)`。
- **索引**：`idx_history_user_viewed(user_id,deleted,viewed_at,id)` 支撑全类型倒序；`idx_history_user_type(user_id,deleted,target_type,viewed_at,id)` 支撑类型筛选。
- **逻辑删除**：单删和清空均置 `deleted=1`，不影响目标或其他资产；再次浏览恢复原行并更新 `viewed_at`。
- **关联/一致性**：目标采用与收藏相同的逻辑关联和公开性校验。每次浏览事务先锁对应 `users` 行，串行化该用户的足迹写入，再 upsert/恢复目标行，最后把超出 50 条的最旧活跃行置删；淘汰与写入必须同一事务提交。所有足迹写入遵守同一用户锁顺序，避免并发绕过上限。

## 4. 媒体与内容

茶、文章和商品同时支持本系统媒体与种子外部图片：封面响应优先使用有效绑定的 `cover_media_id`，为空时回退到 `cover_url`；图集优先按 `gallery_media_keys` 的顺序解析绑定媒体，该数组为空时回退到 `gallery_urls` 的有序 URL。外部 URL 仅允许管理员或种子导入写入，必须是长度不超过 2048 的合法 HTTPS URL，主机名命中腾讯云 COS/CDN 等受控域名白名单，不得包含用户信息，不接受 HTTP、`data:`、`javascript:`、本机路径、内网/回环地址或经重定向绕过的地址。外部 URL 只是受控内容引用，不创建媒体记录，也不要求 `cover_media_id` 有值；发布和更新时均执行上述校验。

### 4.1 `media_file`：媒体文件

| 关键列 | 类型 | 空值 | 默认 | 含义 |
| --- | --- | --- | --- | --- |
| `media_id` | `VARCHAR(64)` | 否 | 无 | 对外稳定媒体 ID |
| `owner_user_id` | `BIGINT` | 否 | 无 | 上传所有者 |
| `purpose` | `VARCHAR(16)` | 否 | 无 | `avatar/post/content/product` |
| `media_type` | `VARCHAR(16)` | 否 | 无 | `IMAGE/VIDEO` |
| `storage_provider` | `VARCHAR(16)` | 否 | 无 | `LOCAL/S3/COS/EXTERNAL`；LOCAL 仅 dev/test，COS 为生产默认，EXTERNAL 仅受控种子迁移 |
| `bucket_name` | `VARCHAR(128)` | 是 | `NULL` | 对象存储 bucket |
| `object_key` | `VARCHAR(512)` | 否 | 无 | 服务端随机对象名或种子唯一键，不使用原始文件名做路径 |
| `external_url` | `VARCHAR(2048)` | 是 | `NULL` | EXTERNAL 的受控 HTTPS 原地址，仅种子迁移使用 |
| `original_filename` | `VARCHAR(255)` | 是 | `NULL` | 仅用于展示的原始文件名 |
| `mime_type` | `VARCHAR(64)` | 否 | 无 | 经内容检测确认的 MIME |
| `size_bytes` | `BIGINT` | 否 | 无 | 文件字节数，应用限制大小 |
| `sha256` | `CHAR(64)` | 否 | 无 | 内容摘要，用于校验和审计 |
| `width`/`height` | `INT` | 是 | `NULL` | 图片/视频像素尺寸 |
| `duration_ms` | `INT` | 是 | `NULL` | 视频时长，首版最多 60 秒 |
| `status` | `VARCHAR(16)` | 否 | `'UPLOADED'` | `UPLOADED/BOUND/DELETED` |
| `bound_type` | `VARCHAR(16)` | 是 | `NULL` | 绑定目标类型，如 user/post/tea/article/product/config |
| `bound_id` | `VARCHAR(64)` | 是 | `NULL` | 目标公开稳定 ID |

- **主键/唯一约束**：主键 `id`；`uk_media_media_id(media_id)`；`uk_media_object(storage_provider,bucket_name,object_key)`。MySQL 唯一键允许多个 `NULL` bucket，但本地对象也必须保证 object key 唯一。
- **索引**：`idx_media_owner_status(owner_user_id,purpose,status,deleted)`、`idx_media_binding(bound_type,bound_id)`、`idx_media_sha256(sha256)`。
- **逻辑删除**：业务删除只标记状态与 `deleted`，不立即物理删对象；被订单快照或公开内容引用的媒体不得清理。
- **关联/一致性**：`owner_user_id` 物理关联用户；`bound_type+bound_id` 是多类型**逻辑关联**。对象写入成功而元数据失败时补偿删除对象；绑定与业务发布/更新同事务。媒体必须校验所有者、用途、实际类型、大小和 `UPLOADED` 状态，不能跨用户或跨用途重放。

### 4.2 `tea`：茶百科

| 关键列 | 类型 | 空值 | 默认 | 含义 |
| --- | --- | --- | --- | --- |
| `business_key` | `VARCHAR(64)` | 否 | 无 | 前端稳定茶 ID |
| `slug` | `VARCHAR(128)` | 否 | 无 | 唯一公开别名 |
| `name` | `VARCHAR(128)` | 否 | 无 | 茶名 |
| `aliases` | `JSON` | 否 | 应用写 `[]` | 有序别名数组 |
| `category` | `VARCHAR(16)` | 否 | 无 | `green/white/yellow/oolong/black/dark` |
| `origin_province/city/district` | `VARCHAR(64)` | 是 | `NULL` | 三级产地 |
| `summary` | `VARCHAR(1024)` | 否 | 无 | 摘要 |
| `processes/aromas/flavors/scenes` | `JSON` | 否 | 应用写 `[]` | 工艺、香气、滋味、适饮场景有序数组 |
| `brewing` | `JSON` | 否 | 无 | 投茶量、水量、水温、器具、浸泡和耐泡参数 |
| `storage_method` | `VARCHAR(1024)` | 是 | `NULL` | 储存方法 |
| `aging_note`/`drinking_note` | `TEXT` | 是 | `NULL` | 保质陈化与饮用提示 |
| `cover_media_id` | `BIGINT` | 是 | `NULL` | 可选封面媒体，优先于外部封面 URL |
| `cover_url` | `VARCHAR(2048)` | 是 | `NULL` | 可选受控腾讯云等 HTTPS 外部封面 URL |
| `gallery_media_keys` | `JSON` | 否 | 应用写 `[]` | 可选绑定图集 media ID 有序数组 |
| `gallery_urls` | `JSON` | 是 | `NULL` | 可选受控外部图集 URL 有序数组；`NULL` 与空数组均表示无外部图集 |
| `content_blocks` | `JSON` | 否 | 应用写 `[]` | 七类受控正文块 |
| `related_product_keys` | `JSON` | 否 | 应用写 `[]` | 关联商品 business key 有序数组 |
| `featured` | `TINYINT` | 否 | `0` | 是否精选，`0/1` |
| `sort` | `INT` | 否 | `0` | 运营排序值 |
| `status` | `VARCHAR(16)` | 否 | `'draft'` | `draft/published/offline` |
| `published_at` | `DATETIME(6)` | 是 | `NULL` | 首次发布时间 |

- **主键/唯一约束**：主键 `id`；`uk_tea_business_key`、`uk_tea_slug` 各自唯一。
- **索引**：`idx_tea_public_sort(status,deleted,sort,id)`、`idx_tea_category(category,status,deleted,sort,id)`、`idx_tea_featured(featured,status,deleted,sort,id)`。
- **逻辑删除**：下架用 `status='offline'`，删除用 `deleted=1`；公开查询二者都过滤。
- **关联/一致性**：封面可物理关联媒体；图集和关联商品 key 为逻辑关联。发布事务校验 brewing 正数与 min/max、所有 JSON 块、媒体归属/用途及关联目标。`relatedTeas` 不存冗余结果，按公开茶 `sort,id` 实时取前三款。

### 4.3 `article`：文章

| 关键列 | 类型 | 空值 | 默认 | 含义 |
| --- | --- | --- | --- | --- |
| `business_key` | `VARCHAR(64)` | 否 | 无 | 前端稳定文章 ID |
| `slug` | `VARCHAR(128)` | 否 | 无 | 唯一公开别名 |
| `title` | `VARCHAR(255)` | 否 | 无 | 标题 |
| `summary` | `VARCHAR(1024)` | 否 | 无 | 摘要 |
| `category` | `VARCHAR(32)` | 否 | 无 | 六类文章栏目 |
| `tags` | `JSON` | 否 | 应用写 `[]` | 标签有序数组 |
| `cover_media_id` | `BIGINT` | 是 | `NULL` | 可选封面媒体，优先于外部封面 URL |
| `cover_url` | `VARCHAR(2048)` | 是 | `NULL` | 可选受控腾讯云等 HTTPS 外部封面 URL |
| `gallery_media_keys` | `JSON` | 否 | 应用写 `[]` | 可选绑定图集 media ID 有序数组 |
| `gallery_urls` | `JSON` | 是 | `NULL` | 可选受控外部图集 URL 有序数组；`NULL` 与空数组均表示无外部图集 |
| `content_blocks` | `JSON` | 否 | 无 | 七类受控正文块，保存完整结构 |
| `related_tea_keys` | `JSON` | 否 | 应用写 `[]` | 关联茶 business key 数组 |
| `read_minutes` | `INT` | 否 | `1` | 展示阅读分钟数 |
| `view_count_display` | `BIGINT` | 否 | `0` | 展示阅读量，不是实际访问统计 |
| `featured` | `TINYINT` | 否 | `0` | 是否精选 |
| `sort` | `INT` | 否 | `0` | 运营排序 |
| `status` | `VARCHAR(16)` | 否 | `'draft'` | `draft/published/offline` |
| `published_at` | `DATETIME(6)` | 是 | `NULL` | 首次发布时间，重复发布不覆盖 |

- **主键/唯一约束**：主键 `id`；`uk_article_business_key`、`uk_article_slug` 各自唯一。
- **索引**：`idx_article_public_sort(status,deleted,sort,id)`、`idx_article_category(category,status,deleted,sort,id)`、`idx_article_featured(featured,status,deleted,sort,id)`、`idx_article_published(published_at,id)`。
- **逻辑删除**：已发布文章须先撤回再编辑或删除；删除保留被历史、收藏引用的记录。
- **关联/一致性**：封面物理关联媒体，`related_tea_keys` 是稳定字符串逻辑关联。发布事务校验标题、摘要、分类、正文和媒体；重复发布若已发布直接返回成功且不修改首次 `published_at`，非法编辑状态返回 409。详情关联茶只取当前发布茶，按全局 `sort,id` 取前三款。

### 4.4 `app_config`：公开运营配置

| 关键列 | 类型 | 空值 | 默认 | 含义 |
| --- | --- | --- | --- | --- |
| `config_key` | `VARCHAR(64)` | 否 | `'production'` | 首版固定配置 ID |
| `config_version` | `VARCHAR(32)` | 否 | 无 | 对客户端公开的配置版本 |
| `payload` | `JSON` | 否 | 无 | 节气、Banner、精选 ID、公告、wiki、热门词、开关等 |
| `active_quiz_version` | `VARCHAR(32)` | 是 | `NULL` | 当前启用问卷版本 |
| `revision` | `BIGINT` | 否 | `0` | 乐观锁版本，每次修改递增 |
| `updated_by` | `BIGINT` | 是 | `NULL` | 最近修改管理员 |

- **主键/唯一约束**：主键 `id`；`uk_app_config_key(config_key)`。
- **索引**：首版单行配置无需额外二级索引。
- **逻辑删除**：公开只读 `config_key='production'` 且未删除行；不保存第二份隐式 mock 配置。
- **关联/一致性**：精选茶/文章、Banner 媒体、百科专题文章等均在 `payload` 中保存稳定公开 key，属于逻辑关联。管理员更新采用 `revision` 条件更新并校验全部 ID；首页解析按配置数组顺序，省略失效项，茶最多四款。payload 仅含公开配置，禁止密钥和内部备注。

## 5. 问卷与推荐

### 5.1 `quiz_definition`：问卷版本

| 关键列 | 类型 | 空值 | 默认 | 含义 |
| --- | --- | --- | --- | --- |
| `quiz_version` | `VARCHAR(32)` | 否 | 无 | 不可变问卷版本 |
| `questions` | `JSON` | 否 | 无 | 恰 15 题的有序题目与选项，不含客户端不可见权重 |
| `scoring_rules` | `JSON` | 否 | 无 | 服务端评分权重与规则，不在公开问卷响应返回 |
| `status` | `VARCHAR(16)` | 否 | `'DISABLED'` | `ENABLED/DISABLED`，启用状态由配置指向 |

- **主键/唯一约束**：主键 `id`；`uk_quiz_version(quiz_version)`。
- **索引**：`idx_quiz_status(status,deleted)`。
- **逻辑删除**：已有推荐引用的版本只禁用、不物理删除。
- **关联/一致性**：推荐结果物理关联本表。发布前校验 15 个固定 key、题内选项 value 唯一、顺序和评分规则完整；版本启用后内容不可原地修改，规则变化必须创建新版本。

### 5.2 `recommendation_result`：推荐结果头

| 关键列 | 类型 | 空值 | 默认 | 含义 |
| --- | --- | --- | --- | --- |
| `result_id` | `CHAR(22)` | 否 | 无 | 至少 128 位随机数 Base64URL 编码，公开不可枚举 |
| `quiz_definition_id` | `BIGINT` | 否 | 无 | 使用的问卷版本 |
| `quiz_version` | `VARCHAR(32)` | 否 | 无 | 版本快照，便于审计 |
| `answers` | `JSON` | 否 | 无 | 15 题原始答案的有序/规范化表示 |
| `answers_hash` | `CHAR(64)` | 否 | 无 | 规范化版本+答案 SHA-256，可用于短期幂等 |

- **主键/唯一约束**：主键 `id`；`uk_recommendation_result_id(result_id)`。不强制全局唯一 answers hash，避免把不同提交永久合并。
- **索引**：`idx_recommendation_hash_created(answers_hash,created_at,id)`，仅供可选短期幂等查询。
- **逻辑删除**：正常读取不删除；过期清理由维护规则决定。
- **关联/一致性**：物理关联问卷版本。创建事务先验证版本和全部答案，再对当前发布茶确定性评分，最后写结果头与全部结果项；不依赖登录或进程内随机状态。相同版本和答案必须得到相同分数、排序和理由。

### 5.3 `recommendation_item`：推荐结果项

| 关键列 | 类型 | 空值 | 默认 | 含义 |
| --- | --- | --- | --- | --- |
| `result_pk` | `BIGINT` | 否 | 无 | 推荐结果头内部 ID |
| `tea_id` | `BIGINT` | 否 | 无 | 评分时茶的内部 ID |
| `tea_business_key` | `VARCHAR(64)` | 否 | 无 | 稳定茶 ID 快照 |
| `rank_no` | `INT` | 否 | 无 | 完整评分名次；首版响应取 Top 3 |
| `score` | `INT` | 否 | 无 | 主分，0～39 |
| `trait_score` | `INT` | 否 | `0` | 六维特征整数总分；每维命中得 1、未命中得 0，合计 0～6，仅用于同分排序 |
| `match_percent` | `INT` | 否 | 无 | 展示匹配度，按固定公式计算，非概率 |
| `reasons` | `JSON` | 否 | 应用写 `[]` | 最多三条有序理由 |

- **主键/唯一约束**：主键 `id`；`uk_recommendation_item_rank(result_pk,rank_no)`；`uk_recommendation_item_tea(result_pk,tea_id)`。
- **索引**：`idx_recommendation_item_result(result_pk,rank_no)`。
- **逻辑删除**：随结果保留，不因茶下架而修改评分记录。
- **关联/一致性**：`result_pk`、`tea_id` 分别物理关联结果和茶。读取时 join 当前 `published` 且未删除茶，过滤已下线项，不重算、不重排原分数。理由、分数和排序在创建后不可变。

## 6. 社区

### 6.1 `post`：帖子

| 关键列 | 类型 | 空值 | 默认 | 含义 |
| --- | --- | --- | --- | --- |
| `business_key` | `VARCHAR(64)` | 否 | 无 | 对外稳定帖子 ID |
| `author_id` | `BIGINT` | 否 | 无 | 作者用户 ID |
| `client_request_id` | `VARCHAR(64)` | 否 | 无 | 发布幂等键 |
| `request_hash` | `CHAR(64)` | 否 | 无 | 规范化发布内容摘要，识别同键不同请求 |
| `content` | `VARCHAR(1200)` | 否 | 无 | 正文；应用按 Unicode 码点限制 300，而非依赖字节长度 |
| `topic` | `VARCHAR(16)` | 否 | 无 | `tasting/brewing/teaware/daily/question` |
| `tags` | `JSON` | 否 | 应用写 `[]` | 最多五个有序标签 |
| `related_tea_id` | `BIGINT` | 是 | `NULL` | 关联茶内部 ID |
| `status` | `VARCHAR(16)` | 否 | `'published'` | `published/hidden/deleted` |
| `like_count` | `BIGINT` | 否 | `0` | 活跃点赞权威计数，不小于 0 |
| `comment_count` | `BIGINT` | 否 | `0` | 未删除评论权威计数，不小于 0 |
| `published_at` | `DATETIME(6)` | 否 | 无 | 发布时间，用于全局排序 |

- **主键/唯一约束**：主键 `id`；`uk_post_business_key(business_key)`；`uk_post_author_request(author_id,client_request_id)`。
- **索引**：`idx_post_public(status,deleted,published_at,id)`，支撑公开过滤并按 `published_at DESC,id DESC` 稳定排序；`idx_post_author(author_id,deleted,published_at,id)`；`idx_post_related_tea(related_tea_id)`。
- **逻辑删除**：作者删除在同事务设置 `deleted=1,status='deleted'`；重复删除直接成功，不重复清理互动。
- **关联/一致性**：作者、关联茶可物理关联；作者公开资料实时 join 用户，不保存可伪造昵称。发布事务内写帖子、绑定媒体和 `post_media`；同幂等键先返回首次结果，若 request hash 不同则 409。隐藏/删除后公共详情、评论、点赞和新增评论均不可用。

### 6.2 `post_media`：帖子媒体顺序

| 关键列 | 类型 | 空值 | 默认 | 含义 |
| --- | --- | --- | --- | --- |
| `post_id` | `BIGINT` | 否 | 无 | 所属帖子 |
| `media_pk` | `BIGINT` | 否 | 无 | 媒体内部 ID |
| `media_role` | `VARCHAR(16)` | 否 | `'IMAGE'` | `IMAGE/VIDEO` |
| `sort_order` | `INT` | 否 | `0` | 图片顺序，从 0 递增 |

- **主键/唯一约束**：主键 `id`；`uk_post_media_order(post_id,sort_order)`；`uk_post_media_media(media_pk)` 确保一个媒体只用于一个帖子。
- **索引**：`idx_post_media_post(post_id,deleted,sort_order)`。
- **逻辑删除**：删帖不物理删除绑定；互动历史保留但公开不可访问。
- **关联/一致性**：两列均物理关联。发布事务校验最多 9 图或 1 视频且互斥，同时把媒体状态改为 `BOUND`、写绑定目标；任何一步失败，帖子、顺序行和媒体绑定全部回滚。

### 6.3 `post_like`：点赞

| 关键列 | 类型 | 空值 | 默认 | 含义 |
| --- | --- | --- | --- | --- |
| `post_id` | `BIGINT` | 否 | 无 | 被点赞帖子 |
| `user_id` | `BIGINT` | 否 | 无 | 点赞用户 |
| `liked_at` | `DATETIME(6)` | 否 | 无 | 最近点赞时间 |

- **主键/唯一约束**：主键 `id`；`uk_post_like(post_id,user_id)`。
- **索引**：`idx_post_like_user(user_id,deleted,liked_at,id)`；唯一键支撑按帖子查用户 liked。
- **逻辑删除**：取消点赞置 `deleted=1`，再点赞恢复原行并刷新 `liked_at`。
- **关联/一致性**：物理关联帖子和用户。PUT/DELETE 均在单事务内锁帖子并校验公开状态，再锁/插入唯一关系行；只有活跃状态实际发生变化时才将 `post.like_count` 加一或减一，计数不得小于 0。重复请求直接返回当前权威 `{liked,likeCount}`。

### 6.4 `post_comment`：评论

| 关键列 | 类型 | 空值 | 默认 | 含义 |
| --- | --- | --- | --- | --- |
| `business_key` | `VARCHAR(64)` | 否 | 无 | 对外评论 ID |
| `post_id` | `BIGINT` | 否 | 无 | 所属帖子 |
| `author_id` | `BIGINT` | 否 | 无 | 评论作者 |
| `client_request_id` | `VARCHAR(64)` | 否 | 无 | 发布幂等键 |
| `request_hash` | `CHAR(64)` | 否 | 无 | 规范化帖子与正文摘要 |
| `content` | `VARCHAR(800)` | 否 | 无 | 评论正文，应用限制 200 个 Unicode 码点 |

- **主键/唯一约束**：主键 `id`；`uk_comment_business_key(business_key)`；`uk_comment_author_request(author_id,client_request_id)`。
- **索引**：`idx_comment_post_created(post_id,deleted,created_at,id)` 支撑公开正序分页；`idx_comment_author(author_id,deleted,created_at,id)`。
- **逻辑删除**：仅评论作者可删，置 `deleted=1`；重复删除不再减计数。
- **关联/一致性**：物理关联帖子和作者。创建事务锁帖子、校验可见性、插入评论并将 `comment_count` 加一；同键同 hash 返回首次评论，不同 hash 返回 409。首次删除在同一事务置删并减一，计数不小于 0。首版无回复、楼中楼和评论点赞。

## 7. 商品、购物车与交易

### 7.1 `product_category`：商品分类

| 关键列 | 类型 | 空值 | 默认 | 含义 |
| --- | --- | --- | --- | --- |
| `business_key` | `VARCHAR(64)` | 否 | 无 | 对外稳定分类 ID |
| `name` | `VARCHAR(64)` | 否 | 无 | 分类名 |
| `slug` | `VARCHAR(128)` | 否 | 无 | 唯一别名 |
| `sort` | `INT` | 否 | `0` | 展示顺序 |
| `enabled` | `TINYINT` | 否 | `1` | 是否启用 |

- **主键/唯一约束**：主键 `id`；business key 和 slug 分别唯一。
- **索引**：`idx_product_category_sort(enabled,deleted,sort,id)`。
- **逻辑删除**：已有商品引用时只禁用/逻辑删除，不物理删除。
- **关联/一致性**：商品通过内部 category ID 物理关联；禁用分类不自动下架商品，管理端须显式处理商品状态。

### 7.2 `product`：商品

| 关键列 | 类型 | 空值 | 默认 | 含义 |
| --- | --- | --- | --- | --- |
| `business_key` | `VARCHAR(64)` | 否 | 无 | 前端稳定商品 ID |
| `product_code` | `VARCHAR(64)` | 否 | 无 | 唯一商品编码 |
| `slug` | `VARCHAR(128)` | 否 | 无 | 唯一公开别名 |
| `category_id` | `BIGINT` | 否 | 无 | 商品分类 |
| `primary_tea_id` | `BIGINT` | 是 | `NULL` | 主关联茶 |
| `related_tea_keys` | `JSON` | 否 | 应用写 `[]` | 关联茶稳定 key 集合 |
| `name` | `VARCHAR(255)` | 否 | 无 | 商品名 |
| `subtitle` | `VARCHAR(512)` | 是 | `NULL` | 副标题 |
| `cover_media_id` | `BIGINT` | 是 | `NULL` | 可选封面媒体，优先于外部封面 URL |
| `cover_url` | `VARCHAR(2048)` | 是 | `NULL` | 可选受控腾讯云等 HTTPS 外部封面 URL |
| `gallery_media_keys` | `JSON` | 否 | 应用写 `[]` | 可选绑定图集 media ID 有序数组 |
| `gallery_urls` | `JSON` | 是 | `NULL` | 可选受控外部图集 URL 有序数组；`NULL` 与空数组均表示无外部图集 |
| `tags` | `JSON` | 否 | 应用写 `[]` | 商品标签，参与搜索 |
| `detail_blocks` | `JSON` | 否 | 应用写 `[]` | 与文章相同的受控详情块 |
| `featured` | `TINYINT` | 否 | `0` | 精选标记 |
| `sort` | `INT` | 否 | `0` | 稳定排序值 |
| `status` | `VARCHAR(16)` | 否 | `'DRAFT'` | `DRAFT/ON_SALE/OFF_SHELF` |

- **主键/唯一约束**：主键 `id`；business key、product code、slug 分别唯一。
- **索引**：`idx_product_public_sort(status,deleted,sort,id)`、`idx_product_category(category_id,status,deleted,sort,id)`、`idx_product_tea(primary_tea_id,status,deleted)`、`idx_product_featured(featured,status,deleted,sort,id)`。
- **逻辑删除**：订单引用的商品只下架/逻辑删除，不物理删除；历史订单只读快照。
- **关联/一致性**：分类、主关联茶和封面可物理关联；图集与关联茶 key 是逻辑关联。上下架事务校验详情、媒体、关联和至少一个可用 SKU；公开详情只返回启用且未删除 SKU，列表价格来自默认可用 SKU。

### 7.3 `product_sku`：商品规格与库存

| 关键列 | 类型 | 空值 | 默认 | 含义 |
| --- | --- | --- | --- | --- |
| `business_key` | `VARCHAR(64)` | 否 | 无 | 对外稳定 SKU ID |
| `product_id` | `BIGINT` | 否 | 无 | 所属商品 |
| `sku_name` | `VARCHAR(128)` | 否 | 无 | 规格名 |
| `sku_code` | `VARCHAR(64)` | 否 | 无 | 全局唯一 SKU 编码 |
| `price_fen` | `BIGINT` | 否 | 无 | 当前售价分 |
| `original_price_fen` | `BIGINT` | 否 | 无 | 划线价分，须大于等于售价 |
| `enabled` | `TINYINT` | 否 | `1` | 是否启用 |
| `is_default` | `TINYINT` | 否 | `0` | 是否默认规格，由应用保证每商品最多一个 |
| `stock_on_hand` | `BIGINT` | 否 | `0` | 在手库存，支付成功后扣减 |
| `stock_reserved` | `BIGINT` | 否 | `0` | 待支付订单预占库存 |
| `version` | `BIGINT` | 否 | `0` | 乐观锁/报价变化检测版本 |

- **主键/唯一约束**：主键 `id`；`uk_sku_business_key(business_key)`、`uk_sku_code(sku_code)`、`uk_sku_product_name(product_id,sku_name)`。
- **索引**：`idx_sku_product(product_id,deleted,enabled,is_default,id)`。
- **逻辑删除**：被订单引用的 SKU 不物理删除；停用不影响历史快照。
- **关联/一致性**：`product_id` 物理关联商品。数据库 CHECK 约束保证金额非负、`original_price_fen>=price_fen`、`stock_on_hand>=0`、`stock_reserved>=0` 且 `stock_reserved<=stock_on_hand`；可用库存为两库存列之差。价格或库存变更递增 `version`，默认规格切换在同一事务完成。

### 7.4 `cart_item`：购物车行

| 关键列 | 类型 | 空值 | 默认 | 含义 |
| --- | --- | --- | --- | --- |
| `user_id` | `BIGINT` | 否 | 无 | 购物车所有者 |
| `product_id` | `BIGINT` | 否 | 无 | 商品内部 ID |
| `sku_id` | `BIGINT` | 否 | 无 | SKU 内部 ID |
| `quantity` | `INT` | 否 | `1` | 数量 1～99，不代表锁库存 |
| `checked` | `TINYINT` | 否 | `1` | 是否勾选 |
| `last_added_at` | `DATETIME(6)` | 否 | 无 | 最近加购时间 |

- **主键/唯一约束**：主键 `id`；`uk_cart_user_sku(user_id,product_id,sku_id)`。
- **索引**：唯一键支撑用户购物车读取；`idx_cart_user_checked(user_id,deleted,checked)` 支撑汇总。
- **逻辑删除**：移除/清空置删；再次加购恢复原行并合并数量。
- **关联/一致性**：物理关联用户、商品和 SKU。加购事务锁用户目标行或使用唯一键 upsert，再核对合并后数量不超过 99 和当时可用库存；购物车不预占。读取时实时 join 当前商品/SKU 计算价格、小计、勾选总额和 `invalidReason`，失效行不得静默消失。

### 7.5 `shop_order`：订单

| 关键列 | 类型 | 空值 | 默认 | 含义 |
| --- | --- | --- | --- | --- |
| `order_no` | `VARCHAR(40)` | 否 | 无 | 唯一公开订单号，服务端生成 |
| `user_id` | `BIGINT` | 否 | 无 | 下单用户 |
| `client_request_id` | `VARCHAR(64)` | 否 | 无 | 创建订单幂等键 |
| `request_hash` | `CHAR(64)` | 否 | 无 | 规范化购物车行/数量、自提和备注摘要，不含客户端金额 |
| `order_status` | `VARCHAR(24)` | 否 | `'PENDING_PAYMENT'` | `PENDING_PAYMENT/PAID/CANCELLED/CLOSED` |
| `payment_status` | `VARCHAR(16)` | 否 | `'UNPAID'` | `UNPAID/PAYING/PAID` |
| `fulfillment_status` | `VARCHAR(24)` | 否 | `'NOT_STARTED'` | `NOT_STARTED/PENDING_PICKUP/COMPLETED` |
| `payment_mode` | `VARCHAR(16)` | 是 | `NULL` | 首次发起支付固定为 `WECHAT/DEMO`，之后不可变 |
| `currency` | `CHAR(3)` | 否 | `'CNY'` | 币种 |
| `item_total_fen` | `BIGINT` | 否 | 无 | 服务端计算的商品总额 |
| `delivery_fee_fen` | `BIGINT` | 否 | `0` | 首版到店自取，固定 0 |
| `discount_fen` | `BIGINT` | 否 | `0` | 首版固定 0 |
| `payable_fen` | `BIGINT` | 否 | 无 | 应付总额，等于商品总额 |
| `pickup_info` | `JSON` | 否 | 无 | 下单时自提信息快照 |
| `remark` | `VARCHAR(500)` | 是 | `NULL` | 用户备注 |
| `quote_hash` | `CHAR(64)` | 是 | `NULL` | 服务端报价内容摘要 |
| `quote_expires_at` | `DATETIME(6)` | 是 | `NULL` | 报价过期 UTC 时间，仅用于创建订单前校验 |
| `payment_expires_at` | `DATETIME(6)` | 否 | 无 | 待支付订单的支付截止 UTC 时间，超时关单依据 |
| `paid_at`/`cancelled_at`/`closed_at` | `DATETIME(6)` | 是 | `NULL` | 对应状态首次发生时间 |
| `completed_at` | `DATETIME(6)` | 是 | `NULL` | 自提核销时间 |
| `version` | `BIGINT` | 否 | `0` | 订单状态乐观锁版本 |

- **主键/唯一约束**：主键 `id`；`uk_order_no(order_no)`；`uk_order_user_request(user_id,client_request_id)`。
- **索引**：`idx_order_user_created(user_id,deleted,created_at,id)`、`idx_order_status_expiry(order_status,payment_expires_at,id)` 供超时关单扫描；`idx_order_payment_status(payment_status,order_status)`。
- **逻辑删除**：普通用户无删除订单能力；逻辑删除不替代取消/关单状态机。
- **关联/一致性**：物理关联用户。创建事务先按幂等键查首次订单：同 hash 返回，不同 hash 409；随后锁定选中购物车行，按 SKU 内部 ID 升序执行条件预占，创建订单和快照项，最后只扣减成交购物车数量。任一步失败全部回滚。价格、SKU 版本、库存或报价过期变化均返回冲突，不信任客户端金额。

### 7.6 `order_item`：订单项快照

| 关键列 | 类型 | 空值 | 默认 | 含义 |
| --- | --- | --- | --- | --- |
| `order_id` | `BIGINT` | 否 | 无 | 所属订单 |
| `product_id` | `BIGINT` | 否 | 无 | 下单时商品 ID |
| `sku_id` | `BIGINT` | 否 | 无 | 下单时 SKU ID |
| `product_business_key` | `VARCHAR(64)` | 否 | 无 | 商品稳定 ID 快照 |
| `sku_code` | `VARCHAR(64)` | 否 | 无 | SKU 编码快照 |
| `product_name` | `VARCHAR(255)` | 否 | 无 | 商品名快照 |
| `sku_name` | `VARCHAR(128)` | 否 | 无 | 规格名快照 |
| `unit_price_fen` | `BIGINT` | 否 | 无 | 服务端成交单价 |
| `original_price_fen` | `BIGINT` | 否 | 无 | 下单时划线价 |
| `quantity` | `INT` | 否 | 无 | 成交数量，大于 0 |
| `subtotal_fen` | `BIGINT` | 否 | 无 | `unit_price_fen*quantity`，服务端计算 |
| `product_snapshot` | `JSON` | 否 | 无 | 封面、标签、必要商品字段快照 |
| `sku_snapshot` | `JSON` | 否 | 无 | 规格、编码和必要 SKU 字段快照 |

- **主键/唯一约束**：主键 `id`；`uk_order_item_sku(order_id,sku_id)`，首版每个 SKU 在一个订单中只有一行。
- **索引**：`idx_order_item_order(order_id,id)`、`idx_order_item_product(product_id,sku_id)`。
- **逻辑删除**：正常业务不删除；订单取消也保留项以支持库存释放与审计。
- **关联/一致性**：物理关联订单、商品和 SKU，但展示只读快照，商品后续修改、下架或逻辑删除均不改变历史。所有金额非负，订单项小计之和必须等于订单商品总额。

### 7.7 `payment_transaction`：支付交易

| 关键列 | 类型 | 空值 | 默认 | 含义 |
| --- | --- | --- | --- | --- |
| `payment_no` | `VARCHAR(40)` | 否 | 无 | 服务端唯一支付号，也是发给渠道的本系统交易标识 |
| `order_id` | `BIGINT` | 否 | 无 | 所属订单 |
| `client_request_id` | `VARCHAR(64)` | 否 | 无 | 发起支付幂等键 |
| `request_hash` | `CHAR(64)` | 否 | 无 | 订单、渠道和支付请求规范化摘要 |
| `channel` | `VARCHAR(16)` | 否 | 无 | `WECHAT/DEMO` |
| `amount_fen` | `BIGINT` | 否 | 无 | 支付金额，必须等于订单应付金额 |
| `currency` | `CHAR(3)` | 否 | `'CNY'` | 币种 |
| `status` | `VARCHAR(16)` | 否 | `'PAYING'` | `PAYING/PAID/FAILED/CLOSED` |
| `provider_prepay_id` | `VARCHAR(128)` | 是 | `NULL` | 微信预支付 ID，仅服务端使用 |
| `provider_trade_no` | `VARCHAR(64)` | 是 | `NULL` | 第三方成功交易号 |
| `provider_status` | `VARCHAR(64)` | 是 | `NULL` | 最近可信渠道状态，用于审计 |
| `paid_at` | `DATETIME(6)` | 是 | `NULL` | 可信支付成功时间 |
| `closed_at` | `DATETIME(6)` | 是 | `NULL` | 支付关闭时间 |
| `last_notified_at` | `DATETIME(6)` | 是 | `NULL` | 最近验签通知时间 |
| `version` | `BIGINT` | 否 | `0` | 支付状态版本 |

- **主键/唯一约束**：主键 `id`；`uk_payment_no(payment_no)`；`uk_payment_order_request(order_id,client_request_id)`；`uk_payment_provider_trade_no(provider_trade_no)`，多个未支付行的 `NULL` 不冲突。
- **索引**：`idx_payment_order_status(order_id,status,created_at,id)`、`idx_payment_status_created(status,created_at,id)` 供查单和超时处理。
- **逻辑删除**：支付记录是审计数据，正常业务只推进状态，不删除。
- **关联/一致性**：物理关联订单。发起支付锁订单并校验归属、待支付状态、渠道能力、金额和幂等 hash；失败后允许新建支付尝试。真实支付成功只能由验签通知或主动查单触发，DEMO 必须显式开关且订单永久保留 `payment_mode='DEMO'`。

## 8. 跨模块事务与并发策略

### 8.1 库存预占、支付与释放

库存以 `product_sku.stock_on_hand/stock_reserved`、`order_item.quantity` 和订单状态共同表达，不另设可被绕过的库存服务：

1. **创建订单**：在单事务内按 `sku_id` 升序处理所有项，使用条件更新使 `stock_reserved` 增加成交数量，且更新条件包含“增加后不超过 `stock_on_hand`”。影响行数不是 1 即库存不足，整单回滚。此原子条件更新保证两个用户争购最后一件时只有一个成功。
2. **购物车扣除**：锁定的购物车行以下单时数量为准，执行 `当前数量-成交数量`；结果为零则逻辑删除，大于零则保留剩余数量。下单期间的新增加购因行锁等待，不会被整行误删。
3. **支付成功**：先验证回调签名、商户/订单/金额与渠道状态，再按固定顺序锁支付行、订单行和涉及 SKU。只有订单仍为 `PENDING_PAYMENT` 时，才把订单改为 `PAID`、履约改为 `PENDING_PICKUP`，并对每项同时减少 `stock_reserved` 和 `stock_on_hand`。重复通知确认已是同一成功交易后直接返回，不再次扣库存。
4. **取消/关单**：锁订单，仅允许从 `PENDING_PAYMENT` 进入 `CANCELLED/CLOSED`，再按订单项逐项减少 `stock_reserved`。状态只跃迁一次，所以重复取消、超时关单与通知乱序不会重复释放。
5. **竞争处理**：支付成功与用户取消都先锁同一订单行，后到者看到终态后不执行库存操作。所有库存操作使用相同 SKU 顺序，降低死锁；数据库异常整体回滚，回调可安全重试。

报价可以是有签名、带过期时间的服务端报价载荷，不必新增报价表；创建订单仍须重新读取商品、SKU 和库存。报价只用于发现变化，绝不能替代服务端计价或库存条件更新。

### 8.2 幂等与状态机

- 发布帖子、评论、创建订单、发起支付均依赖“所有者/订单 + `client_request_id`”唯一约束，不采用先查后插作为唯一保障。保存规范化 `request_hash`：同键同请求返回首次资源，同键不同请求返回 409。
- 收藏、点赞、历史、购物车复用唯一行并恢复逻辑删除，正向和反向请求都幂等，不使用 toggle。
- 订单只允许规格中定义的跃迁；支付回调不能把已支付订单倒退为 `PAYING`，关闭订单不能再支付。管理员核销只允许已支付且 `PENDING_PICKUP` 的订单进入 `COMPLETED`。
- 第三方支付号、订单号和支付号均由数据库唯一约束兜底。金额、币种和渠道不一致的成功通知必须拒绝并告警，不能为了“兼容回调”修改订单金额。

### 8.3 计数与查询口径

- 点赞和评论的关系行变更与 `post.like_count/comment_count` 更新在同一事务，只有关系活跃状态变化才调整计数；计数有非负 CHECK。可提供只读对账任务，分别统计未删除关系并报告差异，但不在请求路径用无锁重算覆盖并发计数。
- 个人中心收藏数与收藏列表均统计未删除记录，失效目标仍计入；足迹数统计未删除足迹，帖子数按“本人可见”口径统计。
- 积分余额以流水为审计依据、用户余额为查询缓存，两者在同一事务更新；等级不落库，避免门槛调整后产生陈旧值。
- 列表统一稳定排序：公开内容 `sort ASC,id ASC`，帖子 `published_at DESC,id DESC`，评论 `created_at ASC,id ASC`，收藏/足迹分别按 `active_at/viewed_at DESC,id DESC`。先完成公开过滤与关键词匹配，再排序分页。

### 8.4 关联、搜索与删除边界

- 收藏/历史通过类型分派到四张目标表，不把 `target_id` 伪装成可建立单一外键的列。配置、内容 JSON 和媒体通用绑定同理，由服务层解析稳定公开 ID 并校验公开性。
- 茶、文章、商品的 JSON 搜索字段在首版可由应用进行受控子串/二字片段匹配；数据量增长后再引入中文全文索引或专用搜索，但必须保持“公开过滤→匹配→稳定排序→分页”的语义。
- 逻辑删除不等于公开下架：管理隐藏、下架和作者删除分别使用明确状态。物理清理由独立维护任务执行，必须先证明没有订单快照、公开内容或绑定关系引用；迁移不级联清除用户资产和交易审计。
