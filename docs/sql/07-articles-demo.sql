-- 确认已经完成 06-articles.sql 后，由你在 DBCode 分段执行。
-- 使用前端已有文章的编号、标题和分类，但正文是精简练习内容，并非完整迁移。
-- 本文件不会自动执行；不使用覆盖更新或忽略冲突，避免悄悄修改已有文章。
SELECT DATABASE();
SELECT id, title, status FROM articles ORDER BY id;

-- 新表为空时，执行本段 INSERT，一次即可。
START TRANSACTION;

INSERT INTO articles
    (id, title, summary, category, cover, content_blocks, status, published_at)
VALUES
    (
        'article_six_types',
        '一张表认识中国六大茶类',
        '文章模块的第一篇练习内容，用来学习列表与详情查询。',
        'tea_basics',
        'https://zhangyue311-1434539871.cos.ap-beijing.myqcloud.com/picgo/20260927124040891.jpg',
        '[{"type":"heading","level":3,"text":"认识文章内容块"},{"type":"paragraph","text":"这是一篇六大茶类主题的练习文章。一个正文数组可以依次保存标题、段落和图片等内容块。"}]',
        'published',
        '2026-11-01 09:00:00'
    ),
    (
        'article_water_temperature',
        '水温不是越高越好：一份泡茶温度指南',
        '第二篇已发布的练习文章，用来比较分类筛选与发布时间排序。',
        'brewing',
        NULL,
        '[{"type":"heading","level":3,"text":"冲泡主题练习"},{"type":"paragraph","text":"这是一篇冲泡主题的练习文章，后续会通过详情接口读取这些内容块。"}]',
        'published',
        '2026-11-01 10:00:00'
    ),
    (
        'article_storage',
        '茶叶储存：先分清保鲜还是陈化',
        '这是一篇尚未发布的草稿，用来验证公开接口的可见性。',
        'storage',
        NULL,
        '[{"type":"heading","level":3,"text":"尚未发布"},{"type":"paragraph","text":"这篇文章仍在编辑中。公开文章列表和公开详情接口都不应返回它。"}]',
        'draft',
        NULL
    );

-- 先查看，确认 3 行数据、2 篇 published 和 1 篇 draft。
SELECT id, title, category, status, published_at
FROM articles
ORDER BY id;

-- 数据正确后，再选中执行 COMMIT；如需取消，在提交前执行 ROLLBACK。
-- COMMIT;

-- 提交后，单独执行以下查询。后续公开文章列表会使用相同的状态条件。
-- published_at 相同时再按 id 排序，保证分页顺序稳定。
SELECT id, title, category, published_at
FROM articles
WHERE status = 'published'
ORDER BY published_at DESC, id ASC;

SELECT id, JSON_TYPE(content_blocks) AS body_type, JSON_LENGTH(content_blocks) AS block_count
FROM articles
ORDER BY id;
