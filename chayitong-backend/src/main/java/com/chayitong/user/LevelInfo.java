package com.chayitong.user;

public record LevelInfo(
        int index,
        String name,
        String description,
        long points,
        String nextName,
        Long nextThreshold,
        long remainingPoints,
        int percent,
        boolean maxed) {

    private static final long[] THRESHOLDS = {0, 100, 250, 450, 700, 1000, 1400, 2000, 3000};
    private static final String[] NAMES = {"初识茶味", "闻香识茶", "一盏知春", "茶席常客", "辨味识韵",
            "壶中岁月", "茶事精进", "通晓茶性", "茶中博士"};
    private static final String[] DESCRIPTIONS = {"初次接触茶文化", "开始辨别茶香", "感受春日茶韵", "熟悉茶席与茶事",
            "能够辨析滋味与韵味", "在茶中沉淀岁月", "持续精进茶事", "通晓不同茶性", "深入精通茶文化"};

    public static LevelInfo of(long points) {
        long safePoints = Math.max(0, points);
        int level = 0;
        for (int i = 1; i < THRESHOLDS.length && safePoints >= THRESHOLDS[i]; i++) {
            level = i;
        }
        if (level == THRESHOLDS.length - 1) {
            return new LevelInfo(9, NAMES[level], DESCRIPTIONS[level], safePoints, null, null, 0, 100, true);
        }
        long next = THRESHOLDS[level + 1];
        int percent = (int) Math.min(99, safePoints * 100 / next);
        return new LevelInfo(level + 1, NAMES[level], DESCRIPTIONS[level], safePoints, NAMES[level + 1], next,
                next - safePoints, percent, false);
    }
}
