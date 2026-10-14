package com.carrental.api;

import com.carrental.domain.CarType;
import com.carrental.service.Availability;
import com.carrental.service.ReservationService;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/availability")
public class AvailabilityController {

    private final ReservationService service;

    public AvailabilityController(ReservationService service) {
        this.service = service;
    }

    @GetMapping
    public Availability get(@RequestParam CarType carType, @RequestParam Instant start, @RequestParam int days) {
        return service.availability(carType, start, days);
    }
}
