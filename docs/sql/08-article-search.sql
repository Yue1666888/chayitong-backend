-- 文章搜索只读练习，由你在 DBCode 执行，无需改表或插入数据。
SELECT DATABASE();

-- 分类筛选，只返回已发布文章。
SELECT id, title, summary, category, published_at
FROM articles
WHERE status = 'published'
  AND category = 'brewing'
ORDER BY published_at DESC, id ASC;

-- 标题或摘要任意一处命中，同时必须已发布且分类一致。
SELECT id, title, summary, category, published_at
FROM articles
WHERE status = 'published'
  AND category = 'brewing'
  AND (title LIKE '%水温%' OR summary LIKE '%水温%')
ORDER BY published_at DESC, id ASC
LIMIT 5 OFFSET 0;

-- 不依赖文章数据，直接观察 AND/OR 的运算分组。
-- 第一列相当于 (FALSE AND FALSE) OR TRUE，结果 1。
-- 第二列为 FALSE AND (FALSE OR TRUE)，结果 0。
SELECT
    FALSE AND FALSE OR TRUE AS without_group,
    FALSE AND (FALSE OR TRUE) AS with_group;
