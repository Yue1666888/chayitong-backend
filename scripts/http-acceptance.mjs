const base = (process.env.BASE_URL || "http://localhost:8080").replace(/\/$/, "");
const results = [];

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

async function request(method, path, { token, body, headers = {} } = {}) {
  const response = await fetch(`${base}${path}`, {
    method,
    headers: {
      ...(body && !(body instanceof FormData) ? { "Content-Type": "application/json" } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
  });
  const text = await response.text();
  let payload = null;
  if (text && response.headers.get("content-type")?.includes("json")) {
    try { payload = JSON.parse(text); } catch { throw new Error(`${method} ${path} 返回非法 JSON`); }
  }
  return { status: response.status, payload, text, traceId: response.headers.get("x-trace-id") };
}

function expect(response, status, name) {
  assert(response.status === status, `${name}: 期望 ${status}，实际 ${response.status} ${response.text}`);
  if (status >= 400) assert(response.payload?.code, `${name}: 缺少错误码`);
  else assert(response.payload?.code === "00000", `${name}: 成功业务码错误 ${response.text}`);
  assert(response.traceId, `${name}: 缺少 X-Trace-Id`);
  results.push(name);
  return response.payload?.data;
}

async function login(code) {
  return expect(await request("POST", "/api/auth/wechat/login", { body: { code } }), 200, `微信登录 ${code}` );
}

const health = await request("GET", "/api/health");
expect(health, 200, "健康检查");
expect(await request("POST", "/api/auth/wechat/login", { body: { code: "invalid" } }), 401, "无效微信凭证拒绝");
expect(await request("POST", "/api/auth/refresh", { body: { refreshToken: " " } }), 401, "空白刷新令牌拒绝");
const home = expect(await request("GET", "/api/home"), 200, "首页聚合");
assert(home.featuredTeas?.length > 0, "首页没有精选茶");
const teas = expect(await request("GET", "/api/teas?pageNum=1&pageSize=100"), 200, "茶列表");
assert(teas.total === 57, `茶总数应为57，实际${teas.total}`);
const articles = expect(await request("GET", "/api/articles?pageNum=1&pageSize=100"), 200, "文章列表");
assert(articles.total === 20, `文章总数应为20，实际${articles.total}`);
const products = expect(await request("GET", "/api/products?pageNum=1&pageSize=100"), 200, "商品列表");
assert(products.total === 30, `商品总数应为30，实际${products.total}`);
expect(await request("GET", "/api/teas/tea_longjing"), 200, "茶详情");
expect(await request("GET", "/api/articles/article_six_types"), 200, "文章详情");
expect(await request("GET", "/api/posts?pageNum=1&pageSize=5"), 200, "社区公开列表");
const search = expect(await request("GET", `/api/search?keyword=${encodeURIComponent("普洱茶")}`), 200, "联合搜索");
assert(search.teas.total > 0, "二字片段搜索未命中");
expect(await request("GET", "/api/search?keyword=%20"), 400, "空白搜索拒绝");

const auth = await login(`accept-${Date.now()}`);
const token = auth.accessToken;
assert(token && auth.refreshToken, "登录未返回双令牌");
expect(await request("GET", "/api/me", { token }), 200, "当前用户");
expect(await request("PATCH", "/api/me/profile", { token, body: { displayName: "验收茶友", bio: "真实HTTP验收", favoriteTea: "绿茶" } }), 200, "资料修改");
expect(await request("POST", "/api/me/daily-points", { token }), 200, "每日积分首次");
const repeatedPoints = expect(await request("POST", "/api/me/daily-points", { token }), 200, "每日积分重复");
assert(repeatedPoints.awarded === false, "同日重复积分不应发放");
expect(await request("PUT", "/api/me/favorites/tea/tea_longjing", { token }), 200, "收藏茶");
expect(await request("PUT", "/api/me/favorites/tea/tea_longjing", { token }), 200, "重复收藏");
expect(await request("GET", "/api/me/favorites?type=tea", { token }), 200, "收藏列表");
expect(await request("DELETE", "/api/me/favorites/tea/tea_longjing", { token }), 200, "取消收藏");
expect(await request("PUT", "/api/me/history/article/article_six_types", { token }), 200, "写浏览历史");
expect(await request("PUT", "/api/me/history/article/article_six_types", { token }), 200, "历史去重");
expect(await request("GET", "/api/me/history", { token }), 200, "历史列表");

const answers = {
  taste: "fresh", aroma: "floral", texture: "crisp", strength: "light", aftertaste: "clean",
  temp: "cold", time: "morning", scene: "work", vessel: "glass", effort: "quick",
  season: "spring", pairing: "snack", explore: "classic", experience: "new", focus: "leaf",
};
const recommendation = expect(await request("POST", "/api/recommendations", { body: { quizVersion: "v1", answers } }), 200, "荐茶计算");
const recommendedIds = recommendation.recommendations.map((item) => item.tea.id);
assert(JSON.stringify(recommendedIds) === JSON.stringify(["tea_huangshan_maofeng", "tea_biluochun", "tea_longjing"]), `荐茶Top3错误 ${recommendedIds}`);
expect(await request("GET", `/api/recommendations/${recommendation.resultId}`), 200, "荐茶结果恢复");

const postRequest = { clientRequestId: `post-${Date.now()}`, content: "真实 HTTP 验收动态", topic: "daily", tags: ["验收"], imageMediaIds: [], relatedTeaId: "tea_longjing" };
const post = expect(await request("POST", "/api/posts", { token, body: postRequest }), 200, "发布动态");
const postId = post.id;
expect(await request("POST", "/api/posts", { token, body: postRequest }), 200, "重复发布幂等");
expect(await request("PUT", `/api/posts/${postId}/like`, { token }), 200, "点赞");
expect(await request("PUT", `/api/posts/${postId}/like`, { token }), 200, "重复点赞");
const comment = expect(await request("POST", `/api/posts/${postId}/comments`, { token, body: { clientRequestId: `comment-${Date.now()}`, content: "验收评论" } }), 200, "发布评论");
expect(await request("DELETE", `/api/posts/${postId}/comments/${comment.comment.id}`, { token }), 200, "删除评论");
expect(await request("DELETE", `/api/posts/${postId}`, { token }), 200, "删除动态");
expect(await request("GET", `/api/posts/${postId}`), 404, "已删动态不可见");

const cart = expect(await request("POST", "/api/cart/items", { token, body: { productId: "product_longjing_50", skuId: "product_longjing_50:50g", quantity: 2 } }), 200, "加入购物车");
const cartItem = cart.items.find((item) => item.productId === "product_longjing_50");
assert(cartItem && cart.totalFen === 33600, `购物车金额错误 ${JSON.stringify(cart)}`);
const quote = expect(await request("POST", "/api/checkout/quote", { token, body: { cartItemIds: [cartItem.id], pickup: {} } }), 200, "结算报价");
assert(quote.payableFen === 33600, "报价金额错误");
const orderRequest = { clientRequestId: `order-${Date.now()}`, quoteId: quote.quoteId, cartItemIds: [cartItem.id], pickup: {}, remark: "HTTP验收" };
const order = expect(await request("POST", "/api/orders", { token, body: orderRequest }), 200, "创建订单");
assert(order.payableFen === 33600 && order.status === "PENDING_PAYMENT", `订单金额或状态错误 ${JSON.stringify(order)}`);
expect(await request("POST", "/api/orders", { token, body: orderRequest }), 200, "订单幂等重试");
const payment = expect(await request("POST", `/api/orders/${order.orderNo}/payments`, { token, body: { mode: "DEMO" } }), 200, "DEMO支付");
assert(payment.paymentStatus === "PAID" && payment.demoNotice, "DEMO支付标记缺失");
expect(await request("GET", `/api/orders/${order.orderNo}/payment-result`, { token }), 200, "支付结果查询");

const png = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=", "base64");
const form = new FormData();
form.append("purpose", "avatar");
form.append("file", new Blob([png], { type: "image/png" }), "avatar.png");
const media = expect(await request("POST", "/api/media", { token, body: form }), 200, "上传头像");
assert(media.mediaId && media.url, "媒体响应缺少ID或URL");

expect(await request("POST", "/api/auth/logout", { token }), 200, "退出登录");
expect(await request("GET", "/api/me", { token }), 401, "退出后令牌失效");

console.log(JSON.stringify({ status: "PASS", checks: results.length, results }, null, 2));
