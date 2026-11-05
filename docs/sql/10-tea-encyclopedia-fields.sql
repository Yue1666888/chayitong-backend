-- 在 DBCode 选中 chayitong，先单独执行 SHOW，确认四个字段都尚未创建。
SHOW COLUMNS FROM teas
WHERE Field IN ('summary', 'origin_province', 'origin_city', 'origin_area');

ALTER TABLE teas
    ADD COLUMN summary VARCHAR(255) NULL,
    ADD COLUMN origin_province VARCHAR(50) NULL,
    ADD COLUMN origin_city VARCHAR(50) NULL,
    ADD COLUMN origin_area VARCHAR(100) NULL;
