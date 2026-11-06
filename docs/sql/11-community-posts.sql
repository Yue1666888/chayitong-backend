-- 在 DBCode 中选中 chayitong。先执行 01-core-schema.sql 建立 users。
CREATE TABLE posts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    author_id VARCHAR(64) NOT NULL,
    content VARCHAR(300) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_posts_author FOREIGN KEY (author_id) REFERENCES users (id),
    INDEX idx_posts_created_at_id (created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
