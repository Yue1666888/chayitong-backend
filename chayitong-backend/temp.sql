SELECT
  DATABASE();

CREATE TABLE teas (
  id VARCHAR(20) PRIMARY KEY,
  name VARCHAR(50) NOT NULL,
  type VARCHAR(20) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

DESCRIBE teas;

INSERT INTO
  teas (id, name, type)
VALUES
  ('tea001', '龙井', '绿茶'),
  ('tea002', '碧螺春', '绿茶'),
  ('tea003', '铁观音', '乌龙茶');

SELECT
  id,
  name,
  type
FROM
  teas
ORDER BY
  id;

SELECT
  id,
  name,
  type
FROM
  teas
WHERE
  type = '绿茶'
ORDER BY
  id;

SELECT
  id,
  name,
  type
FROM
  teas
WHERE
  type = '绿茶'
  AND name = '龙井';

SELECT
  id,
  name,
  type
FROM
  teas
WHERE
  type = '红茶';

UPDATE teas
SET
  name = '西湖龙井'
WHERE
  id = 'tea001';

SELECT
  id,
  name,
  type
FROM
  teas
WHERE
  id = 'tea001';

INSERT INTO
  teas (id, name, type)
VALUES
  ('tea_delete_demo', '删除练习茶', '绿茶');

SELECT
  id,
  name,
  type
FROM
  teas
WHERE
  id = 'tea_delete_demo';

DELETE FROM teas
WHERE
  id = 'tea_delete_demo';

UPDATE teas
SET
  name = '洞庭碧螺春'
WHERE
  id = 'tea002';

SELECT
  id,
  name,
  type
FROM
  teas
ORDER BY
  id
LIMIT
  2
OFFSET
  0;

SELECT
  id,
  name,
  type
FROM
  teas
ORDER BY
  id
LIMIT
  2
OFFSET
  2;

SELECT
  COUNT(*) AS total
FROM
  teas;

SELECT
  id,
  name
FROM
  teas
WHERE
  id IN ('tea001', 'tea002')
ORDER BY
  id;

UPDATE teas
SET
  name = '事务练习-龙井'
WHERE
  id = 'tea001';

UPDATE teas
SET
  name = '事务练习-碧螺春'
WHERE
  id = 'tea002';

ROLLBACK;

CREATE TABLE users (
  id VARCHAR(64) PRIMARY KEY,
  display_name VARCHAR(50) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO
  users (id, display_name)
VALUES
  ('user001', '茶友小林'),
  ('user002', '茶友小周');

CREATE TABLE tea_favorites (
  user_id VARCHAR(64) NOT NULL,
  tea_id VARCHAR(20) NOT NULL,
  PRIMARY KEY (user_id, tea_id),
  CONSTRAINT fk_favorites_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_favorites_tea FOREIGN KEY (tea_id) REFERENCES teas (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO
  tea_favorites (user_id, tea_id)
VALUES
  ('user001', 'tea001'),
  ('user001', 'tea002'),
  ('user002', 'tea001');

SELECT
  user_id,
  tea_id
FROM
  tea_favorites
ORDER BY
  user_id,
  tea_id;

SELECT
  u.display_name AS user_name,
  t.name AS tea_name
FROM
  tea_favorites AS f
  JOIN users AS u ON f.user_id = u.id
  JOIN teas AS t ON f.tea_id = t.id
WHERE
  f.user_id = 'user001'
ORDER BY
  f.tea_id;

INSERT INTO
  user_credentials (user_id, login_name, password_hash)
VALUES
  (
    'user001',
    'xiaolin',
    '{bcrypt}$2a$10$6ZbC1AwJdWUQ6JGvCGaRP.QztX6bqZ72wr/5M8.goltAC1l/1mtsi'
  );

SELECT
  DATABASE();

SHOW COLUMNS
FROM
  users LIKE 'role';

ALTER TABLE users
ADD COLUMN role VARCHAR(16) COLLATE utf8mb4_bin NOT NULL DEFAULT 'USER',
ADD CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN'));

SELECT
  u.id,
  u.display_name,
  u.role,
  c.login_name
FROM
  users AS u
  LEFT JOIN user_credentials AS c ON c.user_id = u.id
ORDER BY
  u.id;

START TRANSACTION;

UPDATE users
SET role = 'ADMIN'
WHERE
  id = 'user001';

SELECT
  id,
  display_name,
  role
FROM
  users
WHERE
  id = 'user001';

SELECT
  id,
  title,
  category,
  published_at
FROM
  articles
WHERE
  status = 'published'
ORDER BY
  published_at DESC,
  id ASC;