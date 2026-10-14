package com.carrental.config;

import com.carrental.domain.Fleet;
import com.carrental.repository.JsonFileReservationRepository;
import com.carrental.repository.ReservationRepository;
import com.carrental.service.AvailabilityService;
import com.carrental.service.ReservationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CarRentalProperties.class)
public class CarRentalConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    Fleet fleet(CarRentalProperties properties) {
        return new Fleet(properties.fleet());
    }

    @Bean
    ReservationRepository reservationRepository(CarRentalProperties properties, ObjectMapper mapper) {
        return new JsonFileReservationRepository(Path.of(properties.dataFile()), mapper);
    }

    @Bean
    AvailabilityService availabilityService(ReservationRepository repository, Fleet fleet) {
        return new AvailabilityService(repository, fleet);
    }

    @Bean
    ReservationService reservationService(ReservationRepository repository, AvailabilityService availability,
                                          Clock clock, CarRentalProperties properties) {
        return new ReservationService(repository, availability, clock, properties.maxRentalDays());
    }
}
