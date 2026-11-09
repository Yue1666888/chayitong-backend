# 茶一统后端技术设计

## 1. 架构原则

采用单体 Spring Boot 服务与 MySQL，按业务模块分包。首版不引入微服务、Redis、消息队列或搜索引擎；通过明确事务、唯一索引和稳定排序解决当前规模的一致性问题。

请求链：`Controller → Service → Mapper → MySQL`，Entity 经 Assembler 转为 Response。Controller 只处理 HTTP、参数与统一响应；Service 负责业务规则和事务；复杂搜索在 Mapper XML 中完成，不使用客户端侧过滤。

## 2. 工程结构

Maven 工程仍位于外层仓库的 `chayitong-backend/`，根包调整为 `com.chayitong`，启动类 `ChayitongApplication`。规格位于外层 `specs/`，SQL 位于 Maven 工程的 `sql/`。

- `common`：响应、错误、分页、基础实体、认证上下文和通用校验。
- `config`：Security、MyBatis-Plus、CORS、OpenAPI、媒体与微信配置。
- `auth`、`user`、`media`：身份、个人资产和文件。
- `tea`、`article`、`content`：茶、文章、首页配置。
- `search`、`recommendation`：联合搜索、问卷和推荐。
- `community`：动态、点赞、评论。
- `shop`：商品、SKU、购物车、订单和支付。

每模块采用模板约定的 controller/service/mapper/entity/request/response/assembler，不创建无意义 ServiceImpl。

## 3. API 与标识

- 统一 `/api`，不使用 `/api/v1`；成功码 `00000`，响应 `code/message/data/timestamp/traceId`。
- 分页统一 `list/total/pageNum/pageSize`，默认 1/20，最大 100；社区可按文档进一步限 50。
- 数据库存储内部 BIGINT 雪花 ID。茶、文章、商品、SKU 等前端稳定字符串保存在唯一 `business_key`，公开路径继续使用这些字符串；用户、动态、订单等使用服务端生成的不可预测或唯一标识。
- API 中所有 ID 序列化为字符串，避免 JavaScript 对 Long 精度丢失。
- 时间数据库以 UTC `DATETIME(6)` 保存，API 输出带 Z/偏移的 ISO 8601；每日积分单独使用 Asia/Shanghai 业务日期。
- JSON 仅用于内容块、数组和订单快照等适合整体读写的数据，需要筛选/唯一约束的关联必须拆表。

## 4. 认证与授权

微信登录通过 `RestClient` 调用微信服务端，使用超时、非敏感日志和错误映射。测试以 MockWebServer 或本地 HTTP 替身验证，不在单元测试访问真实微信。

登录后签发 256 位随机 opaque access token 与 refresh token。数据库只存 SHA-256 摘要。自定义 OncePerRequestFilter 解析 `Authorization: Bearer`，查询有效会话并建立 Spring Security Authentication；有效访问令牌过期即 401。刷新在事务内轮换令牌并撤销旧值，退出撤销会话。

授权矩阵：公开内容 GET 免登录；`/api/me/**`、购物车、订单和社区写入需 USER 或 ADMIN；`/api/admin/**` 需 ADMIN。资源归属在 Service 再校验，不只依赖 URL 规则。Bearer 模式不使用 Cookie，因此不启用 Session/CSRF；CORS 不开放凭据 Cookie。

## 5. 数据库与一致性

- 完整结构维护在 `sql/schema/00_chayitong_schema.sql`，全新初始化由 `sql/bootstrap/init.sql` 引用同等结构，增量变更进入 migrations；应用启动不自动改表。
- 内容、用户、动态、评论、商品等使用逻辑删除；点赞和购物车等关系记录可物理删除。
- 唯一约束是幂等最后防线：收藏/历史/点赞目标键、用户+clientRequestId、订单号、第三方交易号、每日积分业务日。
- 收藏/历史多态目标采用逻辑关联，由 TargetService 统一检查公开可用性和组装摘要，数据库不伪装可直接建立多态外键。
- 点赞/评论记录和计数同事务；文章发布锁记录；库存用 `SELECT ... FOR UPDATE` 或条件 UPDATE 原子预占。
- 订单创建事务包含请求指纹、订单与快照、库存预占和购物车数量扣减；任何失败整体回滚。
- 历史最多 50 条在事务中去重/写入/淘汰，唯一键与排序保证可重复执行。

## 6. 内容与搜索

内容块用 Jackson `JsonNode` 加显式校验器，严格检查类型、必填、可选字段和文本长度，拒绝未知字段。Tea/Article/Product 复用同一校验服务。

搜索首版使用参数化 SQL LIKE，转义 `%/_` 等通配符；完整关键词与二字片段规则在 Service 生成匹配条件，公开状态在 SQL 强制。JSON 块可通过受控 JSON 文本匹配完成首版搜索，后续数据量增长再独立引入全文索引，不改变 API。

问卷和评分规则由后端版本化常量/配置定义；推荐结果保存计算快照，读取时只关联发布茶。固定答案回归测试锁定权重、特征分、排序和公式。

## 7. 媒体与外部服务

`MediaStorage` 提供本地、腾讯云 COS 和 S3 实现。本地实现只用于 dev/test；生产默认要求 COS bucket/region/secretId/secretKey，且禁止回退本地。上传先限制请求和文件大小，再检查真实格式，元数据落库失败时补偿删除对象。

结算报价使用 HMAC 签名载荷，`APP_SHOP_QUOTE_SECRET` 必须由环境配置且至少 32 字节，禁止进程启动时随机生成，否则重启或多实例会无法验证报价。`PaymentService` 分离 DEMO 与 WECHAT。DEMO 由显式开关保护，默认生产关闭；WECHAT 在未实现真实商户适配时明确返回能力不可用。支付结果只读数据库可信状态。真实通知、签名和商户验收不在无凭证时伪造完成。

## 8. 种子数据

从前端当前 JS 种子通过可追溯脚本生成 JSON/SQL，避免人工复制 57 款茶、20 篇文章和 30 个商品时产生漂移。生成物记录来源和校验数量，导入 SQL 使用稳定 business_key 和幂等 INSERT/UPSERT；初始化不会读取或清空前端项目，也不会自动清库。

## 9. 测试策略

- 单元测试：推荐算法、等级、内容块、金额与请求指纹。
- `@SpringBootTest + MockMvc`：真实安全过滤、校验、事务、Mapper 和响应序列化。
- 使用 Testcontainers 启动独立 MySQL 8，每次测试类/方法通过事务回滚或唯一数据隔离，不连接现有 chayitong 数据库。
- 标准 `./mvnw test` 即执行真实数据库集成测试；无 Docker 时明确失败并提示，不静默换成语义不同的内存库。
- 最终再启动隔离 Compose 服务，用 HTTP 脚本完成 E01～E10 验收，核对数据库状态和生产开关。

## 10. 可观测与部署

所有响应和日志带 X-Trace-Id；异常不暴露堆栈/SQL/秘密。健康检查只报告应用与必要数据库状态，不返回连接字符串。Docker 镜像两阶段构建，Compose 使用独立命名卷，不沿用旧固定 Windows 数据目录。生产必须显式配置数据库、微信、存储、CORS 和初始化管理员，不使用模板默认密码。
