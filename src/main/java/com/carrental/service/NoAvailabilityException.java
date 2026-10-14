package com.carrental.service;

public class NoAvailabilityException extends RuntimeException {
    public NoAvailabilityException(String message) {
        super(message);
    }
}
