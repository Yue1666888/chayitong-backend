-- 登录凭据表：由你在 DBCode 中手动执行，本文件不会自动初始化数据库。
-- 执行前先结束之前的事务练习，并确认当前选中的数据库是 chayitong。
SELECT DATABASE();

-- users 保存业务用户资料；本表保存该用户的账号密码登录凭据。
-- 它不会创建 MySQL 登录账号，也不涉及 .env 中的数据库连接密码。
CREATE TABLE user_credentials (
    user_id VARCHAR(64) NOT NULL,
    login_name VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,

    PRIMARY KEY (user_id),
    UNIQUE KEY uk_user_credentials_login_name (login_name),

    CONSTRAINT fk_user_credentials_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

-- 一位用户最多对应一组账号密码凭据；login_name 不允许重复。
-- password_hash 将保存 PasswordEncoder 生成的完整编码结果，包括算法前缀。
-- 本节先只建结构，下一节再生成并录入练习账号的有效密码哈希。
DESCRIBE user_credentials;
SELECT COUNT(*) AS credential_count FROM user_credentials;
