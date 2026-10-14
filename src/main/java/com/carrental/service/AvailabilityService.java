package com.carrental.service;

import com.carrental.domain.CarType;
import com.carrental.domain.Fleet;
import com.carrental.domain.TimeWindow;
import com.carrental.repository.ReservationRepository;

public class AvailabilityService {

    private final ReservationRepository repository;
    private final Fleet fleet;

    public AvailabilityService(ReservationRepository repository, Fleet fleet) {
        this.repository = repository;
        this.fleet = fleet;
    }

    /** Cars of a type are interchangeable, so availability is capacity minus overlapping confirmed bookings. */
    public Availability check(CarType type, TimeWindow window) {
        int capacity = fleet.capacity(type);
        int reserved = (int) repository.findAll().stream()
                .filter(r -> r.carType() == type && r.isConfirmed() && r.window().overlaps(window))
                .count();
        return new Availability(type, capacity, reserved, Math.max(0, capacity - reserved));
    }
}
