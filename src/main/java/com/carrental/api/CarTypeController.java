package com.carrental.api;

import com.carrental.domain.CarType;
import com.carrental.domain.Fleet;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/car-types")
public class CarTypeController {

    private final Fleet fleet;

    public CarTypeController(Fleet fleet) {
        this.fleet = fleet;
    }

    @GetMapping
    public List<CarTypeResponse> list() {
        return Arrays.stream(CarType.values())
                .map(t -> new CarTypeResponse(t, fleet.capacity(t)))
                .toList();
    }
}
