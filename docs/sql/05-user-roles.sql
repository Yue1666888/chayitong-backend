-- 角色字段：由你在 DBCode 中分段执行，本文件不会自动初始化数据库。
-- 先结束之前的事务练习，确认连接的是 Docker 中的 chayitong 数据库。
SELECT DATABASE();
SHOW COLUMNS FROM users LIKE 'role';

-- 上面的 SHOW 查询没有结果时，执行下面这条 ALTER TABLE，一次即可。
-- utf8mb4_bin 使本字段区分大小写，CHECK 只接受大写 USER 或 ADMIN。
-- MySQL 的 ALTER TABLE 会隐式提交，不要在尚未完成的事务练习中执行。
ALTER TABLE users
    ADD COLUMN role VARCHAR(16) COLLATE utf8mb4_bin NOT NULL DEFAULT 'USER',
    ADD CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN'));

-- 查看默认角色；不查询密码或密码哈希。
SELECT u.id, u.display_name, u.role, c.login_name
FROM users AS u
LEFT JOIN user_credentials AS c ON c.user_id = u.id
ORDER BY u.id;

-- 管理员设置请按 docs/user-roles.md 的下一步分段执行。
-- 本脚本不自动提升任何用户的角色，也不修改 Java 权限规则。
