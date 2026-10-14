package com.carrental.repository;

import com.carrental.domain.Reservation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository {

    /** Inserts or replaces the reservation with the same id. */
    Reservation save(Reservation reservation);

    Optional<Reservation> findById(UUID id);

    List<Reservation> findAll();
}
