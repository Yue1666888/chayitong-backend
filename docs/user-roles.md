# 角色与权限，第 1 步：数据库准备

本节由你在 DBCode 手动改表和设置练习管理员。
本页记录数据库准备步骤；第 2 步的 Java 角色接入和权限验证见 user-roles-security.md。
不需要重新启动后端来执行本节 SQL。

## 本节目标

认证：登录检查账号密码，确认你是谁。
授权：结合角色及资源归属，判断你能做什么。

本项目先采用一位用户一个角色的简单模型，角色存放在 users.role：

| 角色 | 计划允许的操作 |
|---|---|
| USER | 浏览茶叶、管理自己的收藏 |
| ADMIN | 以上操作，以及新增、修改、删除和批量新增茶叶 |

未登录访客也继续可以浏览茶叶。
管理员角色不会自动获得读取和修改其他用户收藏的权限。
这些规则由 Java 权限配置执行，单独添加数据库字段不会自动改变接口权限。

users 保存业务用户资料和角色；user_credentials 继续保存登录凭据。
这里的 ADMIN 是茶一统业务管理员，与 MySQL 的 root 账号、Docker 和数据库连接密码无关。

## 1. 确认数据库与字段

在 DBCode 中连接 Docker 的 MySQL，选择 chayitong 数据库。
先结束或提交之前尚未结束的事务练习，再执行：

```sql
SELECT DATABASE();
SHOW COLUMNS FROM users LIKE 'role';
```

第一条应返回 chayitong；第二条在本次检查时应为空。
如果 role 已经存在，先查看结构，不要重复执行增加字段的 SQL。

## 2. 增加字段

```sql
ALTER TABLE users
    ADD COLUMN role VARCHAR(16) COLLATE utf8mb4_bin NOT NULL DEFAULT 'USER',
    ADD CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN'));
```

- ALTER TABLE：修改已有表结构。
- ADD COLUMN：增加字段。
- VARCHAR(16)：保存最多 16 个字符的字符串。
- COLLATE utf8mb4_bin：让这个字段按区分大小写的规则比较。
- NOT NULL：不允许 NULL。
- DEFAULT 'USER'：新增记录没有提供 role 时，使用 USER；现有记录在这次改表后也得到 USER。
- CHECK：限制角色只能是 USER 或 ADMIN；chk_users_role 是该约束的名称。

本节 MySQL 为 8.0.46，支持 CHECK 约束。
ALTER TABLE 会隐式提交，不能用普通 ROLLBACK 撤销这次结构修改。

这条 SQL 执行一次即可，完整脚本见 sql/05-user-roles.sql。

## 3. 查看账号与角色

```sql
SELECT u.id, u.display_name, u.role, c.login_name
FROM users AS u
LEFT JOIN user_credentials AS c ON c.user_id = u.id
ORDER BY u.id;
```

这里使用 LEFT JOIN，因此没有账号密码凭据的业务用户也会显示出来。
改表后现有用户应全部是 USER，包括你注册的 xiaoming。

## 4. 设置一位练习管理员

本次检查确认 user001 的登录名是 xiaolin。
先确认上一步查询仍然对应这个账号，再分段执行：

```sql
START TRANSACTION;

UPDATE users
SET role = 'ADMIN'
WHERE id = 'user001';

SELECT u.id, u.role, c.login_name
FROM users AS u
JOIN user_credentials AS c ON c.user_id = u.id
WHERE u.id = 'user001';
```

查询应显示 user001 / ADMIN / xiaolin，确认后执行：

```sql
COMMIT;
```

如果 UPDATE 后发现选错了用户，在 COMMIT 前执行 ROLLBACK，重新选择正确的用户。
UPDATE 是数据修改，可以使用事务回滚；与前面的 ALTER TABLE 区别开。

xiaoming 保持 USER，后面分别用两个账号测试权限。

## 接下来 Java 要做什么

第 2 步已让 UserCredentialRepository 读取 role，并由 LoginUser.getAuthorities()
将 USER / ADMIN 转成 ROLE_USER / ROLE_ADMIN。
SecurityConfig 用 hasRole("ADMIN") 保护茶叶写接口，验证步骤见 user-roles-security.md。

注册请求继续不接受 role，普通注册由数据库默认赋予 USER。
接入角色后，数据库角色变更也不会自动改写已有 Session 中的登录身份，需要退出后重新登录。
