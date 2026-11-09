package com.chayitong.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LevelInfoTests {

    @Test
    void calculatesEveryThresholdAndMaximumLevel() {
        long[] thresholds = {0, 100, 250, 450, 700, 1000, 1400, 2000, 3000};
        for (int i = 0; i < thresholds.length; i++) {
            assertThat(LevelInfo.of(thresholds[i]).index()).isEqualTo(i + 1);
        }
        assertThat(LevelInfo.of(125).percent()).isEqualTo(50);
        assertThat(LevelInfo.of(125).remainingPoints()).isEqualTo(125);
        assertThat(LevelInfo.of(5000).maxed()).isTrue();
        assertThat(LevelInfo.of(5000).percent()).isEqualTo(100);
    }
}
