package com.carrental.domain;

import java.time.Duration;
import java.time.Instant;

/** A half-open time interval [start, end). */
public record TimeWindow(Instant start, Instant end) {

    public TimeWindow {
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("start must be before end");
        }
    }

    public static TimeWindow ofDays(Instant start, int days) {
        return new TimeWindow(start, start.plus(Duration.ofDays(days)));
    }

    /** Back-to-back windows (one ends exactly when the other starts) do not overlap. */
    public boolean overlaps(TimeWindow other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }
}
