package com.carrental.api;

import com.carrental.domain.CarType;
import com.carrental.domain.Reservation;
import com.carrental.domain.ReservationStatus;
import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        String customerName,
        CarType carType,
        Instant startTime,
        Instant endTime,
        int days,
        ReservationStatus status) {

    static ReservationResponse from(Reservation r) {
        return new ReservationResponse(r.id(), r.customerName(), r.carType(), r.startTime(), r.endTime(),
                r.days(), r.status());
    }
}
