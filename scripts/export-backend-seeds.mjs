import fs from "node:fs";
import path from "node:path";
import vm from "node:vm";
import crypto from "node:crypto";
import { createRequire } from "node:module";

const projectRoot = path.resolve(import.meta.dirname, "..");
const frontendRoot = path.resolve(projectRoot, "../02-chayitong");
const seedsDir = path.join(frontendRoot, "miniprogram/data/seeds");
const requireFromHere = createRequire(import.meta.url);
const teas = requireFromHere(path.join(seedsDir, "teas.js"));
const articles = requireFromHere(path.join(seedsDir, "articles.js"));
const products = requireFromHere(path.join(seedsDir, "products.js"));
const posts = requireFromHere(path.join(seedsDir, "posts.js"));
const appConfig = requireFromHere(path.join(seedsDir, "app-config.js"));
const quizSource = fs.readFileSync(path.join(frontendRoot, "miniprogram/subpackages/content/quiz/index.js"), "utf8");
const questions = vm.runInNewContext(`${quizSource}\n;QUESTIONS;`, {
  require: () => ({}),
  Page: () => {},
});

if (teas.length !== 57 || articles.length !== 20 || products.length !== 30 || questions.length !== 15) {
  throw new Error(`种子数量变化：tea=${teas.length}, article=${articles.length}, product=${products.length}, quiz=${questions.length}`);
}

const statements = [
  "SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;",
  "SET time_zone = '+00:00';",
  "USE chayitong;",
];

function hashId(value) {
  const bytes = crypto.createHash("sha256").update(String(value)).digest();
  const id = bytes.readBigUInt64BE(0) & 0x7fffffffffffffffn;
  return (id === 0n ? 1n : id).toString();
}

function hash(value) {
  return crypto.createHash("sha256").update(String(value)).digest("hex");
}

function sql(value) {
  if (value === null || value === undefined) return "NULL";
  if (typeof value === "number") return String(value);
  if (typeof value === "bigint") return value.toString();
  if (typeof value === "boolean") return value ? "1" : "0";
  return `'${String(value).replaceAll("\\", "\\\\").replaceAll("'", "''")}'`;
}

function json(value) {
  return sql(JSON.stringify(value ?? null));
}

function time(value) {
  if (!value) return null;
  return new Date(value).toISOString().replace("T", " ").replace("Z", "").replace(/\.(\d{3})$/, ".$1000");
}

function insert(table, values) {
  const columns = Object.keys(values);
  statements.push(`INSERT IGNORE INTO ${table} (${columns.join(", ")}) VALUES (${columns.map((key) => sql(values[key])).join(", ")});`);
}

for (const tea of teas) {
  insert("tea", {
    id: hashId(tea._id), business_key: tea._id, slug: tea.slug, name: tea.name,
    aliases: JSON.stringify(tea.aliases), category: tea.type,
    origin_province: tea.origin?.province, origin_city: tea.origin?.city, origin_district: tea.origin?.area,
    summary: tea.summary, processes: JSON.stringify(tea.craft), aromas: JSON.stringify(tea.aromas),
    flavors: JSON.stringify(tea.flavors), scenes: JSON.stringify(tea.suitableScenes), brewing: JSON.stringify(tea.brew),
    storage_method: tea.storage?.method, aging_note: tea.storage?.shelfLifeTip, drinking_note: tea.notice,
    cover_url: tea.cover, gallery_urls: JSON.stringify(tea.gallery || []),
    content_blocks: JSON.stringify(tea.contentBlocks), related_product_keys: JSON.stringify(tea.relatedProductIds || []),
    featured: tea.featured, sort: tea.sort, status: tea.status,
    published_at: time(tea.publishedAt || tea.createdAt), created_at: time(tea.createdAt), updated_at: time(tea.updatedAt),
  });
}

for (const article of articles) {
  insert("article", {
    id: hashId(article._id), business_key: article._id, slug: article.slug, title: article.title,
    summary: article.summary, category: article.category, tags: JSON.stringify(article.tags), cover_url: article.cover,
    gallery_urls: JSON.stringify([]), content_blocks: JSON.stringify(article.contentBlocks),
    related_tea_keys: JSON.stringify(article.relatedTeaIds || []), read_minutes: article.readMinutes,
    view_count_display: article.viewCountDisplay, featured: article.featured, sort: article.sort,
    status: article.status, published_at: time(article.publishedAt || article.createdAt),
    created_at: time(article.createdAt), updated_at: time(article.updatedAt),
  });
}

const categoryNames = { tea: "茶叶", teaware: "茶器", gift_box: "礼盒" };
for (const [index, category] of Object.keys(categoryNames).entries()) {
  insert("product_category", {
    id: hashId(`category:${category}`), business_key: category, name: categoryNames[category], slug: category, sort: index,
  });
}

for (const product of products) {
  insert("product", {
    id: hashId(product._id), business_key: product._id, product_code: product.sku, slug: product._id,
    category_id: hashId(`category:${product.categoryId}`), primary_tea_id: product.teaId ? hashId(product.teaId) : null,
    related_tea_keys: JSON.stringify(product.relatedTeaIds || []), name: product.name, subtitle: product.subtitle,
    cover_url: product.cover, gallery_urls: JSON.stringify(product.gallery || []), tags: JSON.stringify(product.tags || []),
    detail_blocks: JSON.stringify(product.detailBlocks || []), featured: product.featured, sort: product.sort,
    status: product.status === "on_sale" ? "ON_SALE" : "OFF_SHELF",
    created_at: time(product.createdAt), updated_at: time(product.updatedAt),
  });
  for (const spec of product.specs) {
    const skuKey = `${product._id}:${spec.id}`;
    insert("product_sku", {
      id: hashId(skuKey), business_key: skuKey, product_id: hashId(product._id), sku_name: spec.name,
      sku_code: `${product.sku}-${spec.id}`, price_fen: spec.priceFen, original_price_fen: spec.originalPriceFen,
      enabled: spec.enabled, is_default: spec.default, stock_on_hand: spec.stockDisplay ?? product.stockDisplay ?? 0,
      created_at: time(product.createdAt), updated_at: time(product.updatedAt),
    });
  }
}

const demoUsers = new Map();
for (const post of posts) {
  if (!demoUsers.has(post.author.id)) demoUsers.set(post.author.id, post.author);
  for (const comment of post.commentsPreview || []) {
    const key = `commenter_${hash(comment.authorName).slice(0, 12)}`;
    if (!demoUsers.has(key)) {
      demoUsers.set(key, { id: key, displayName: comment.authorName, avatar: null, level: 1 });
    }
  }
}

const levelThresholds = [0, 100, 250, 450, 700, 1000, 1400, 2000, 3000];
for (const author of demoUsers.values()) {
  insert("users", {
    id: hashId(author.id), business_key: author.id, display_name: author.displayName,
    points_balance: levelThresholds[Math.max(0, Math.min(8, (author.level || 1) - 1))], status: "ACTIVE",
  });
  if (author.avatar) {
    const mediaKey = `seed_avatar_${author.id}`;
    insert("media_file", {
      id: hashId(mediaKey), media_id: mediaKey, owner_user_id: hashId(author.id), purpose: "avatar", media_type: "IMAGE",
      storage_provider: "EXTERNAL", object_key: mediaKey, external_url: author.avatar, mime_type: "image/jpeg", size_bytes: 1,
      sha256: hash(author.avatar), status: "BOUND", bound_type: "user", bound_id: author.id,
    });
    statements.push(`UPDATE users SET avatar_media_id = ${hashId(mediaKey)} WHERE business_key = ${sql(author.id)} AND avatar_media_id IS NULL;`);
  }
}

function insertExternalMedia(mediaKey, ownerKey, url, purpose, role, boundId) {
  const video = role === "VIDEO";
  insert("media_file", {
    id: hashId(mediaKey), media_id: mediaKey, owner_user_id: hashId(ownerKey), purpose, media_type: role,
    storage_provider: "EXTERNAL", object_key: mediaKey, external_url: url, mime_type: video ? "video/mp4" : "image/jpeg",
    size_bytes: 1, sha256: hash(url), status: "BOUND", bound_type: "post", bound_id: boundId,
  });
}

for (const post of posts) {
  const relatedTeaId = post.relatedTeaId && teas.some((tea) => tea._id === post.relatedTeaId) ? hashId(post.relatedTeaId) : null;
  insert("post", {
    id: hashId(post._id), business_key: post._id, author_id: hashId(post.author.id), client_request_id: `seed_${post._id}`,
    request_hash: hash(post.content), content: post.content, topic: post.topic, tags: JSON.stringify(post.tags || []),
    related_tea_id: relatedTeaId, status: post.status, like_count: post.likeCountDisplay || 0,
    comment_count: (post.commentsPreview || []).length, published_at: time(post.publishedAt),
    created_at: time(post.createdAt), updated_at: time(post.updatedAt),
  });
  (post.images || []).forEach((url, index) => {
    const mediaKey = `seed_${post._id}_image_${index}`;
    insertExternalMedia(mediaKey, post.author.id, url, "post", "IMAGE", post._id);
    insert("post_media", {
      id: hashId(`post_media:${mediaKey}`), post_id: hashId(post._id), media_pk: hashId(mediaKey), media_role: "IMAGE", sort_order: index,
    });
  });
  if (post.video?.url) {
    const mediaKey = `seed_${post._id}_video`;
    insertExternalMedia(mediaKey, post.author.id, post.video.url, "post", "VIDEO", post._id);
    insert("post_media", {
      id: hashId(`post_media:${mediaKey}`), post_id: hashId(post._id), media_pk: hashId(mediaKey), media_role: "VIDEO", sort_order: 0,
    });
  }
  (post.commentsPreview || []).forEach((comment, index) => {
    const authorKey = `commenter_${hash(comment.authorName).slice(0, 12)}`;
    const commentKey = `${post._id}:${comment.id}`;
    insert("post_comment", {
      id: hashId(commentKey), business_key: commentKey, post_id: hashId(post._id), author_id: hashId(authorKey),
      client_request_id: `seed_${commentKey}`, request_hash: hash(comment.content), content: comment.content,
      created_at: time(new Date(new Date(post.publishedAt).getTime() + index * 60000).toISOString()),
      updated_at: time(new Date(new Date(post.publishedAt).getTime() + index * 60000).toISOString()),
    });
  });
}

const heroIds = [];
const covers = new Set();
for (const article of articles) {
  if (article.cover && !covers.has(article.cover)) {
    heroIds.push(article._id);
    covers.add(article.cover);
  }
  if (heroIds.length === 4) break;
}
const payload = {
  ...appConfig,
  banners: appConfig.homeBanners,
  announcement: appConfig.notice,
  features: { ...appConfig.featureFlags, publicCommunityWrite: true },
  demo: { ...appConfig.demo, paymentLabel: "模拟支付", checkoutNotice: "演示订单，不产生真实扣款。" },
  wikiTopics: [
    { id: "brewing", name: "冲泡", note: "水温与冲泡方法", articleId: "article_water_temperature" },
    { id: "storage", name: "储存", note: "家庭存茶", articleId: "article_storage" },
    { id: "teaware", name: "茶器", note: "认识常用茶器", articleId: "article_gaiwan" },
    { id: "history", name: "茶史", note: "茶文化脉络", articleId: "article_tea_history" },
  ],
  wikiHeroArticleIds: heroIds,
  searchHotKeywords: ["西湖龙井", "水温", "盖碗", "六大茶类"],
  seasonalTheme: { ...appConfig.seasonalTheme, recommendedAroma: "花果香", recommendedTemperatureC: 90 },
};
statements.push(`UPDATE app_config SET config_version = ${sql(String(appConfig.version))}, payload = ${json(payload)}, active_quiz_version = 'v1' WHERE config_key = 'production' AND revision = 0;`);
statements.push(`UPDATE quiz_definition SET questions = ${json(questions)} WHERE quiz_version = 'v1';`);

const output = path.join(projectRoot, "chayitong-backend/sql/bootstrap/content_seeds.sql");
fs.writeFileSync(output, `${statements.join("\n")}\n`, "utf8");
console.log(JSON.stringify({ output, teas: teas.length, articles: articles.length, products: products.length, posts: posts.length, users: demoUsers.size, questions: questions.length }, null, 2));
