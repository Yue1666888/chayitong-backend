package com.chayitong.user;

import java.time.LocalDate;

public record DailyPointsResult(LocalDate businessDate, boolean awarded, long points, LevelInfo level) {
}
