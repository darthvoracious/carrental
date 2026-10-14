package com.carrental.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.UUID;

public record Reservation(
        UUID id,
        String customerName,
        CarType carType,
        Instant startTime,
        int days,
        Instant endTime,
        ReservationStatus status,
        Instant createdAt) {

    public static Reservation confirmed(String customerName, CarType carType, Instant startTime, int days, Instant now) {
        TimeWindow window = TimeWindow.ofDays(startTime, days);
        return new Reservation(UUID.randomUUID(), customerName, carType, startTime, days,
                window.end(), ReservationStatus.CONFIRMED, now);
    }

    public TimeWindow window() {
        return new TimeWindow(startTime, endTime);
    }

    @JsonIgnore
    public boolean isConfirmed() {
        return status == ReservationStatus.CONFIRMED;
    }

    public Reservation cancelled() {
        return new Reservation(id, customerName, carType, startTime, days, endTime,
                ReservationStatus.CANCELLED, createdAt);
    }
}
