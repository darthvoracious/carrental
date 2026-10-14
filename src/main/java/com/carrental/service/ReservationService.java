package com.carrental.service;

import com.carrental.domain.CarType;
import com.carrental.domain.Reservation;
import com.carrental.domain.ReservationStatus;
import com.carrental.domain.TimeWindow;
import com.carrental.repository.ReservationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public class ReservationService {

    private final ReservationRepository repository;
    private final AvailabilityService availabilityService;
    private final Clock clock;
    private final int maxRentalDays;
    private final Map<CarType, ReentrantLock> locks = new EnumMap<>(CarType.class);

    public ReservationService(ReservationRepository repository, AvailabilityService availabilityService,
                              Clock clock, int maxRentalDays) {
        this.repository = repository;
        this.availabilityService = availabilityService;
        this.clock = clock;
        this.maxRentalDays = maxRentalDays;
        for (CarType type : CarType.values()) {
            locks.put(type, new ReentrantLock());
        }
    }

    public Reservation reserve(String customerName, CarType carType, Instant startTime, int days) {
        if (customerName == null || customerName.isBlank()) {
            throw new InvalidReservationException("customerName must not be blank");
        }
        if (carType == null) {
            throw new InvalidReservationException("carType is required");
        }
        TimeWindow window = validatedWindow(startTime, days);

        // Check and save must be atomic per car type so two requests cannot take the last car.
        ReentrantLock lock = locks.get(carType);
        lock.lock();
        try {
            Availability availability = availabilityService.check(carType, window);
            if (availability.available() == 0) {
                throw new NoAvailabilityException("No " + carType + " available from " + window.start()
                        + " to " + window.end());
            }
            return repository.save(
                    Reservation.confirmed(customerName.trim(), carType, startTime, days, clock.instant()));
        } finally {
            lock.unlock();
        }
    }

    public Availability availability(CarType carType, Instant startTime, int days) {
        return availabilityService.check(carType, validatedWindow(startTime, days));
    }

    public Reservation get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ReservationNotFoundException(id));
    }

    public List<Reservation> list(CarType carType, ReservationStatus status) {
        return repository.findAll().stream()
                .filter(r -> carType == null || r.carType() == carType)
                .filter(r -> status == null || r.status() == status)
                .toList();
    }

    /** Idempotent: cancelling a cancelled reservation changes nothing. */
    public void cancel(UUID id) {
        Reservation reservation = get(id);
        ReentrantLock lock = locks.get(reservation.carType());
        lock.lock();
        try {
            if (reservation.isConfirmed()) {
                repository.save(reservation.cancelled());
            }
        } finally {
            lock.unlock();
        }
    }

    private TimeWindow validatedWindow(Instant startTime, int days) {
        if (startTime == null) {
            throw new InvalidReservationException("startTime is required");
        }
        if (days < 1 || days > maxRentalDays) {
            throw new InvalidReservationException("days must be between 1 and " + maxRentalDays);
        }
        if (startTime.isBefore(clock.instant())) {
            throw new InvalidReservationException("startTime must not be in the past");
        }
        return TimeWindow.ofDays(startTime, days);
    }
}
