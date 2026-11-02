-- 文章模块第 1 步：由你在 DBCode 分段执行，本文件不会自动运行。
-- 先结束尚未完成的事务练习，确认选中 Docker 中的 chayitong 数据库。
SELECT DATABASE();
SHOW TABLES LIKE 'articles';

-- 如果 articles 尚不存在，执行下面的 CREATE TABLE，一次即可。
-- 若表已经存在，先 SHOW CREATE TABLE articles，不要删除或覆盖已有表。
CREATE TABLE articles (
    id VARCHAR(64) NOT NULL,
    title VARCHAR(100) NOT NULL,
    summary VARCHAR(500) NOT NULL DEFAULT '',
    category VARCHAR(32) NOT NULL,
    cover VARCHAR(1024) NULL,
    content_blocks JSON NOT NULL,
    status VARCHAR(16) COLLATE utf8mb4_bin NOT NULL DEFAULT 'draft',
    published_at DATETIME NULL,

    PRIMARY KEY (id),
    CONSTRAINT chk_articles_status CHECK (status IN ('draft', 'published')),
    CONSTRAINT chk_articles_content_array CHECK (JSON_TYPE(content_blocks) = 'ARRAY')
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

DESCRIBE articles;
SHOW CREATE TABLE articles;

-- JSON 正文要求是数组，数组中的具体内容块字段将在后端读取/写入时另行处理。
-- DATETIME 本身不保存时区，本节约定 published_at 存北京时间的日期和时间。
-- 下一步在 07-articles-demo.sql 中手动录入练习数据。
