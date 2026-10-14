package com.carrental.api;

import com.carrental.domain.CarType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateReservationRequest(
        @NotBlank @Size(max = 100) String customerName,
        @NotNull CarType carType,
        @NotNull Instant startTime,
        @NotNull Integer days) {
}
