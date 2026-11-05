-- 在 DBCode 中选中 chayitong。确认查询没有返回 cover_key 时，再执行 ALTER TABLE。
SHOW COLUMNS FROM teas LIKE 'cover_key';

ALTER TABLE teas
    ADD COLUMN cover_key VARCHAR(255) NULL COMMENT '茶叶封面在对象存储中的 Key';
