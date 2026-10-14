package com.carrental.api;

import com.carrental.domain.CarType;

public record CarTypeResponse(CarType carType, int capacity) {
}
