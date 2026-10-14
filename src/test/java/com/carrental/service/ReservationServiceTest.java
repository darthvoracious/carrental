package com.carrental.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carrental.domain.CarType;
import com.carrental.domain.Fleet;
import com.carrental.domain.Reservation;
import com.carrental.domain.ReservationStatus;
import com.carrental.repository.InMemoryReservationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReservationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant START = Instant.parse("2026-11-01T10:00:00Z");

    private ReservationService service;

    @BeforeEach
    void setUp() {
        service = newService(Map.of(CarType.SEDAN, 2, CarType.SUV, 1, CarType.VAN, 1));
    }

    private static ReservationService newService(Map<CarType, Integer> counts) {
        InMemoryReservationRepository repository = new InMemoryReservationRepository();
        Fleet fleet = new Fleet(counts);
        return new ReservationService(repository, new AvailabilityService(repository, fleet),
                Clock.fixed(NOW, ZoneOffset.UTC), 30);
    }

    @Test
    void reservationGetsEndTimeAndConfirmedStatus() {
        Reservation r = service.reserve("Ana", CarType.SEDAN, START, 3);

        assertThat(r.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(r.endTime()).isEqualTo(Instant.parse("2026-11-04T10:00:00Z"));
        assertThat(r.createdAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsWhenTypeIsFull() {
        service.reserve("Ana", CarType.SUV, START, 2);

        assertThatThrownBy(() -> service.reserve("Bo", CarType.SUV, START.plusSeconds(3600), 1))
                .isInstanceOf(NoAvailabilityException.class);
    }

    @Test
    void cancelledReservationFreesCapacity() {
        Reservation r = service.reserve("Ana", CarType.VAN, START, 2);
        service.cancel(r.id());

        assertThat(service.reserve("Bo", CarType.VAN, START, 2).status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(service.get(r.id()).status()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void cancelIsIdempotentAndUnknownIdFails() {
        Reservation r = service.reserve("Ana", CarType.VAN, START, 2);
        service.cancel(r.id());
        service.cancel(r.id());

        assertThat(service.get(r.id()).status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThatThrownBy(() -> service.cancel(UUID.randomUUID())).isInstanceOf(ReservationNotFoundException.class);
    }

    @Test
    void availabilityCountsOnlyOverlappingConfirmedReservations() {
        service.reserve("Ana", CarType.SEDAN, START, 2);
        Reservation cancelled = service.reserve("Bo", CarType.SEDAN, START, 2);
        service.cancel(cancelled.id());
        service.reserve("Cy", CarType.SEDAN, START.plusSeconds(10 * 86_400L), 1);

        Availability a = service.availability(CarType.SEDAN, START, 2);

        assertThat(a.capacity()).isEqualTo(2);
        assertThat(a.reserved()).isEqualTo(1);
        assertThat(a.available()).isEqualTo(1);
    }

    @Test
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> service.reserve("Ana", CarType.SEDAN, NOW.minusSeconds(1), 1))
                .isInstanceOf(InvalidReservationException.class);
        assertThatThrownBy(() -> service.reserve("Ana", CarType.SEDAN, START, 0))
                .isInstanceOf(InvalidReservationException.class);
        assertThatThrownBy(() -> service.reserve("Ana", CarType.SEDAN, START, 31))
                .isInstanceOf(InvalidReservationException.class);
        assertThatThrownBy(() -> service.reserve(" ", CarType.SEDAN, START, 1))
                .isInstanceOf(InvalidReservationException.class);
    }

    @Test
    void startingExactlyNowIsAllowed() {
        assertThat(service.reserve("Ana", CarType.SEDAN, NOW, 1)).isNotNull();
    }

    @Test
    void listFiltersByTypeAndStatus() {
        service.reserve("Ana", CarType.SEDAN, START, 1);
        Reservation suv = service.reserve("Bo", CarType.SUV, START, 1);
        service.cancel(suv.id());

        assertThat(service.list(null, null)).hasSize(2);
        assertThat(service.list(CarType.SEDAN, null)).hasSize(1);
        assertThat(service.list(null, ReservationStatus.CANCELLED)).extracting(Reservation::id).containsExactly(suv.id());
        assertThat(service.list(CarType.SEDAN, ReservationStatus.CANCELLED)).isEmpty();
    }

    @Test
    void concurrentRequestsNeverOverbook() throws Exception {
        ReservationService single = newService(Map.of(CarType.SEDAN, 3, CarType.SUV, 0, CarType.VAN, 0));
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        List<Future<Void>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Void> task = () -> {
                ready.countDown();
                go.await();
                try {
                    single.reserve("Customer", CarType.SEDAN, START, 2);
                    succeeded.incrementAndGet();
                } catch (NoAvailabilityException e) {
                    rejected.incrementAndGet();
                }
                return null;
            };
            futures.add(pool.submit(task));
        }
        ready.await();
        go.countDown();
        for (Future<Void> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(succeeded.get()).isEqualTo(3);
        assertThat(rejected.get()).isEqualTo(threads - 3);
    }
}
