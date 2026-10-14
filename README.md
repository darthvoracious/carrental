# Car Rental

A simulated car rental reservation system with a REST API. A customer reserves a car of a given type (sedan, SUV or van) from a start date and time for a number of days. The number of cars per type is limited, so a reservation is accepted only if a car of that type is free for the whole rental period.

The design is in [docs/DESIGN_SPEC.md](docs/DESIGN_SPEC.md).

## Prerequisites

- JDK 21 or newer

Maven is not required. The included wrapper downloads it.

## Build and test

```bash
./mvnw test        # Windows: mvnw.cmd test
```

The tests simulate common rental scenarios through the REST API (`ReservationScenariosTest`) and cover the domain rules, persistence and concurrency.

## Run

```bash
./mvnw spring-boot:run
```

The API listens on `http://localhost:8080`. Reservations are saved to `data/reservations.json`, which is created on first start and ignored by git.

## Configuration

`src/main/resources/application.yml`:

```yaml
carrental:
  data-file: data/reservations.json
  max-rental-days: 30
  fleet:
    SEDAN: 5
    SUV: 3
    VAN: 2
```

## API

Base path `/api/v1`. Times are ISO-8601 UTC.

| Method | Path | Purpose | Success | Errors |
|---|---|---|---|---|
| GET | `/car-types` | List car types with fleet size | 200 | |
| GET | `/availability?carType=SUV&start=2026-11-02T10:00:00Z&days=3` | Cars free for a period | 200 | 400 |
| POST | `/reservations` | Create a reservation | 201 | 400, 409 |
| GET | `/reservations?carType=&status=` | List reservations | 200 | 400 |
| GET | `/reservations/{id}` | Get a reservation | 200 | 404 |
| DELETE | `/reservations/{id}` | Cancel (record kept, status `CANCELLED`) | 204 | 404 |

Errors use `application/problem+json`. A 409 means no car of that type is free for the period.

```bash
curl -X POST localhost:8080/api/v1/reservations \
  -H "Content-Type: application/json" \
  -d '{"customerName":"Ana Silva","carType":"SUV","startTime":"2026-11-02T10:00:00Z","days":3}'

curl "localhost:8080/api/v1/availability?carType=SUV&start=2026-11-02T10:00:00Z&days=3"

curl -X DELETE localhost:8080/api/v1/reservations/<id>
```

## Rules

- Start time must not be in the past.
- Rental length is 1 to 30 days. The end time is the start time plus whole days.
- Periods are half-open: a return at 10:00 and a pickup at 10:00 do not conflict.
- Cancelling frees the car immediately and can be repeated safely.
