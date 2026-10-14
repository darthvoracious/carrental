package com.carrental.service;

import java.util.UUID;

public class ReservationNotFoundException extends RuntimeException {
    public ReservationNotFoundException(UUID id) {
        super("Reservation " + id + " not found");
    }
}
