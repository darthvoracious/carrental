package com.carrental.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TimeWindowTest {

    private static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");

    private static TimeWindow window(int startDay, int endDay) {
        return new TimeWindow(T0.plusSeconds(startDay * 86_400L), T0.plusSeconds(endDay * 86_400L));
    }

    @Test
    void ofDaysEndsAfterWholeDays() {
        TimeWindow w = TimeWindow.ofDays(T0, 3);
        assertThat(w.end()).isEqualTo(Instant.parse("2026-11-04T10:00:00Z"));
    }

    @Test
    void partialOverlapIsDetected() {
        assertThat(window(0, 3).overlaps(window(2, 5))).isTrue();
        assertThat(window(2, 5).overlaps(window(0, 3))).isTrue();
    }

    @Test
    void containedWindowOverlaps() {
        assertThat(window(0, 10).overlaps(window(3, 4))).isTrue();
        assertThat(window(3, 4).overlaps(window(0, 10))).isTrue();
    }

    @Test
    void identicalWindowsOverlap() {
        assertThat(window(1, 3).overlaps(window(1, 3))).isTrue();
    }

    @Test
    void backToBackWindowsDoNotOverlap() {
        assertThat(window(0, 3).overlaps(window(3, 5))).isFalse();
        assertThat(window(3, 5).overlaps(window(0, 3))).isFalse();
    }

    @Test
    void disjointWindowsDoNotOverlap() {
        assertThat(window(0, 1).overlaps(window(5, 6))).isFalse();
    }

    @Test
    void emptyOrInvertedWindowIsRejected() {
        assertThatThrownBy(() -> new TimeWindow(T0, T0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TimeWindow(T0.plusSeconds(1), T0)).isInstanceOf(IllegalArgumentException.class);
    }
}
