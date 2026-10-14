package com.carrental.repository;

import com.carrental.domain.Reservation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryReservationRepository implements ReservationRepository {

    private final Map<UUID, Reservation> store = new ConcurrentHashMap<>();

    @Override
    public Reservation save(Reservation reservation) {
        store.put(reservation.id(), reservation);
        return reservation;
    }

    @Override
    public Optional<Reservation> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Reservation> findAll() {
        List<Reservation> all = new ArrayList<>(store.values());
        all.sort(Comparator.comparing(Reservation::createdAt).thenComparing(Reservation::id));
        return all;
    }
}
