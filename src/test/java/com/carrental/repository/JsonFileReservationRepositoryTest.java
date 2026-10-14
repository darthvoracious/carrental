package com.carrental.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carrental.domain.CarType;
import com.carrental.domain.Reservation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonFileReservationRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant START = Instant.parse("2026-11-01T10:00:00Z");

    private final ObjectMapper mapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @TempDir
    Path dir;

    @Test
    void createsMissingFileAndParentDirectory() {
        Path file = dir.resolve("nested/reservations.json");

        new JsonFileReservationRepository(file, mapper);

        assertThat(file).exists().hasContent("[ ]");
    }

    @Test
    void reservationsSurviveRestart() {
        Path file = dir.resolve("reservations.json");
        JsonFileReservationRepository first = new JsonFileReservationRepository(file, mapper);
        Reservation saved = first.save(Reservation.confirmed("Ana", CarType.SUV, START, 3, NOW));
        first.save(saved.cancelled());

        JsonFileReservationRepository second = new JsonFileReservationRepository(file, mapper);

        assertThat(second.findAll()).containsExactly(saved.cancelled());
        assertThat(second.findById(saved.id())).contains(saved.cancelled());
    }

    @Test
    void fileContainsIsoUtcTimestamps() throws Exception {
        Path file = dir.resolve("reservations.json");
        new JsonFileReservationRepository(file, mapper).save(Reservation.confirmed("Ana", CarType.VAN, START, 1, NOW));

        assertThat(Files.readString(file)).contains("\"startTime\" : \"2026-11-01T10:00:00Z\"");
    }

    @Test
    void corruptFileFailsFast() throws Exception {
        Path file = dir.resolve("reservations.json");
        Files.writeString(file, "not json");

        assertThatThrownBy(() -> new JsonFileReservationRepository(file, mapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot read reservation data file");
    }
}
