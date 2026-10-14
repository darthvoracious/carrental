package com.carrental.api;

import com.carrental.domain.CarType;
import com.carrental.domain.Reservation;
import com.carrental.domain.ReservationStatus;
import com.carrental.service.ReservationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody CreateReservationRequest request) {
        Reservation created = service.reserve(request.customerName(), request.carType(),
                request.startTime(), request.days());
        return ResponseEntity.created(URI.create("/api/v1/reservations/" + created.id()))
                .body(ReservationResponse.from(created));
    }

    @GetMapping
    public List<ReservationResponse> list(@RequestParam(required = false) CarType carType,
                                          @RequestParam(required = false) ReservationStatus status) {
        return service.list(carType, status).stream().map(ReservationResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ReservationResponse get(@PathVariable UUID id) {
        return ReservationResponse.from(service.get(id));
    }

    /** Cancels the reservation; the record is kept with status CANCELLED. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        service.cancel(id);
        return ResponseEntity.noContent().build();
    }
}
