-- 空库准备。先在 DBCode 选中目标数据库，确认这三张表尚未建立。
-- 按顺序建立茶叶和用户表，再建立依赖它们的收藏表。
CREATE TABLE teas (
    id VARCHAR(20) PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    type VARCHAR(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE users (
    id VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(50) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tea_favorites (
    user_id VARCHAR(64) NOT NULL,
    tea_id VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, tea_id),
    CONSTRAINT fk_favorites_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_favorites_tea FOREIGN KEY (tea_id) REFERENCES teas (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
