package com.carrental.service;

import com.carrental.domain.CarType;

public record Availability(CarType carType, int capacity, int reserved, int available) {
}
