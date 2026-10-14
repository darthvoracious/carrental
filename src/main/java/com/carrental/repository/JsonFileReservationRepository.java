package com.carrental.repository;

import com.carrental.domain.Reservation;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores reservations as a JSON array in a single file. The file is loaded at startup
 * and rewritten (temp file + move) on every change.
 */
public class JsonFileReservationRepository implements ReservationRepository {

    private static final TypeReference<List<Reservation>> LIST_TYPE = new TypeReference<>() {};

    private final Path file;
    private final ObjectMapper mapper;
    private final Map<UUID, Reservation> store = new LinkedHashMap<>();

    public JsonFileReservationRepository(Path file, ObjectMapper mapper) {
        this.file = file;
        this.mapper = mapper;
        load();
    }

    @Override
    public synchronized Reservation save(Reservation reservation) {
        store.put(reservation.id(), reservation);
        persist();
        return reservation;
    }

    @Override
    public synchronized Optional<Reservation> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public synchronized List<Reservation> findAll() {
        List<Reservation> all = new ArrayList<>(store.values());
        all.sort(Comparator.comparing(Reservation::createdAt).thenComparing(Reservation::id));
        return all;
    }

    private void load() {
        try {
            if (!Files.exists(file)) {
                persist();
                return;
            }
            for (Reservation r : mapper.readValue(file.toFile(), LIST_TYPE)) {
                store.put(r.id(), r);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read reservation data file " + file + ": " + e.getMessage(), e);
        }
    }

    private void persist() {
        try {
            Path parent = file.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            Path tmp = Files.createTempFile(parent, "reservations-", ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), new ArrayList<>(store.values()));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write reservation data file " + file, e);
        }
    }
}
