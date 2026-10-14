# Car Rental Reservation System: Design Spec

Status: **Decisions resolved, awaiting final approval.** No code is written until this spec is approved.

## 1. Goal

A simulated car rental system with a RESTful API. A customer reserves a car of a given type (sedan, SUV, van) starting at a given date and time for a given number of days. Fleet size per type is limited, so a reservation succeeds only if a car of that type is free for the whole period.

Out of scope: UI, console app, authentication, payments, pricing, multiple locations, driver details beyond a customer name.

## 2. Technology

| Concern | Choice | Reason |
|---|---|---|
| Language | Java 21 (LTS) | Records, switch expressions, modern `java.time` |
| Build | Maven with wrapper (`mvnw`) | Clone and run with no installed tooling except a JDK |
| REST framework | Spring Boot 3.x (`spring-boot-starter-web`, `validation`) | Industry standard |
| JSON | Jackson (bundled) | Used for API payloads and file persistence |
| Tests | JUnit 5, AssertJ, Spring `MockMvc`, `@SpringBootTest` | Industry standard |
| CI | GitHub Actions (`mvn verify`) | Proves it builds from a clean checkout |

Commands:
- `./mvnw test` runs all tests.
- `./mvnw spring-boot:run` starts the API on port 8080.

## 3. Domain Model

Domain classes have no Spring or Jackson dependencies, so the rules can be unit tested in isolation.

**`CarType`** (enum): `SEDAN`, `SUV`, `VAN`.

**`Fleet`**: holds the capacity per `CarType`. It is configured through `application.yml`, with defaults of 5 sedans, 3 SUVs and 2 vans. Tests override these values.

**`Reservation`** (immutable record plus status):

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Server-generated |
| `customerName` | String | Required, 1–100 chars |
| `carType` | CarType | Required |
| `startTime` | Instant | UTC, serialized as ISO-8601 |
| `days` | int | 1–30 |
| `endTime` | Instant | Derived: `startTime + days × 24h` |
| `status` | `CONFIRMED` / `CANCELLED` | |
| `createdAt` | Instant | |

**`TimeWindow`** (value object): `[start, end)`, half-open. Two windows overlap when `a.start < b.end && b.start < a.end`. A return at 10:00 and a new pickup at 10:00 do not conflict.

**`AvailabilityService`**: for a car type and a window, it counts the `CONFIRMED` reservations of that type that overlap the window. The type is available if `overlapping < capacity`.

Overlap counting is sufficient because every car of a type is interchangeable. Reservations are not bound to a specific car (see open question 1).

**`ReservationService`**: orchestrates the validation, availability check and save, and cancellation. It uses an injected `java.time.Clock` so tests control "now".

### Business rules
1. `startTime` must not be in the past.
2. `days` is between 1 and 30.
3. A reservation is confirmed only if the type has capacity for the whole window. Otherwise it is rejected.
4. Cancelling a reservation frees capacity immediately. Cancelling twice is idempotent.
5. Capacity checks and saves are atomic per car type. A lock prevents two simultaneous requests from taking the last car.

## 4. REST API

Base path `/api/v1`. All bodies are JSON. Timestamps are ISO-8601 UTC, for example `2026-11-01T10:00:00Z`.

| Method | Path | Purpose | Success | Errors |
|---|---|---|---|---|
| GET | `/car-types` | List types with capacity | 200 | |
| GET | `/availability?carType=SUV&start=...&days=3` | Cars free for a window | 200 | 400 |
| POST | `/reservations` | Create a reservation | 201 plus `Location` header | 400, 409 |
| GET | `/reservations` | List reservations. Filters: `carType`, `status` | 200 | 400 |
| GET | `/reservations/{id}` | Get one reservation | 200 | 404 |
| DELETE | `/reservations/{id}` | Cancel (sets status `CANCELLED`, keeps the record) | 204 | 404 |

**POST `/reservations` request:**
```json
{ "customerName": "Ana Silva", "carType": "SUV", "startTime": "2026-11-01T10:00:00Z", "days": 3 }
```

**Response 201:**
```json
{ "id": "6f1c...", "customerName": "Ana Silva", "carType": "SUV",
  "startTime": "2026-11-01T10:00:00Z", "endTime": "2026-11-04T10:00:00Z",
  "days": 3, "status": "CONFIRMED" }
```

**Errors** use RFC 7807 `application/problem+json`:
- `400`: validation failure (missing field, unknown type, days out of range, start in the past). The body lists the offending fields.
- `404`: reservation not found.
- `409`: no car of the requested type is available for the window.

## 5. Persistence (JSON)

A `ReservationRepository` interface has two implementations:
- `JsonFileReservationRepository` is the default. It writes `data/reservations.json` (path configurable with `carrental.data-file`).
- `InMemoryReservationRepository` is a test double.

The file holds one JSON array of reservations. Writes go to a temp file and are then moved into place atomically, so a crash cannot leave a half-written file. The repository loads the file at startup and creates it if missing. `data/` is git-ignored. Fleet capacity lives in config, not in the data file.

## 6. Project Structure

```
carrental/
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/wrapper/
├── README.md                      build, run, API examples
├── .gitignore                     target/, data/, IDE files
├── .gitattributes                 line endings (mvnw stays LF)
├── .github/workflows/ci.yml
├── docs/DESIGN_SPEC.md
└── src/
    ├── main/java/com/carrental/
    │   ├── CarRentalApplication.java
    │   ├── domain/       CarType, Reservation, ReservationStatus, TimeWindow, Fleet
    │   ├── service/      ReservationService, AvailabilityService, exceptions
    │   ├── repository/   ReservationRepository, JsonFile..., InMemory...
    │   ├── api/          controllers, request/response DTOs, GlobalExceptionHandler
    │   └── config/       FleetProperties, Clock bean
    ├── main/resources/application.yml
    └── test/java/com/carrental/
        ├── domain/       TimeWindowTest
        ├── service/      AvailabilityServiceTest, ReservationServiceTest
        ├── repository/   JsonFileReservationRepositoryTest
        └── api/          ReservationScenariosTest (end-to-end via MockMvc)
```

## 7. Test Plan

Unit tests cover the rules. API scenario tests simulate real use through the REST endpoints with a fixed clock and a small fleet (for example 2 per type) in a temp data file.

**TimeWindow**
- Overlapping, containing, contained, and adjacent windows. Back-to-back (end equals start) is not an overlap.

**Reservation use cases (API scenarios)**
1. Reserve an available sedan: 201, correct `endTime`, status `CONFIRMED`.
2. Reserve each of the three types successfully.
3. Fill a type to capacity, then the next request returns 409.
4. A full type does not block the other types.
5. Same type, non-overlapping dates: both succeed.
6. Back-to-back rentals (return time equals next pickup) succeed.
7. One-hour overlap on the last free car returns 409.
8. A long reservation spans a shorter one: capacity is counted correctly.
9. Cancel a reservation, then the freed car can be reserved again.
10. Cancel twice returns 204 both times. Cancelling an unknown id returns 404.
11. Availability endpoint reports the correct remaining count before and after a booking.
12. List reservations with `carType` and `status` filters. Get by id returns 200 or 404.

**Validation**
- Start in the past, `days` of 0, 31 or negative, unknown car type, blank customer name, malformed date: each returns 400 with field details.

**Persistence**
- Reservations survive a repository reload (restart simulation).
- Missing file is created. Corrupt file fails fast with a clear error.

**Concurrency**
- N parallel requests for the last car: exactly one gets 201, the rest get 409.

## 8. Git Readiness

- `git init` with an initial commit after approval, on `main`.
- Maven wrapper committed, so `git clone && ./mvnw test` works on a machine with only JDK 21.
- `.gitignore` excludes `target/`, `data/`, `.idea/`, `.vscode/`, `*.iml`.
- The README documents prerequisites, build, test, run, and `curl` examples for each endpoint.
- Commit messages and the README use a neutral teammate voice with no tool attribution, per your global rules.

## 9. Decisions

1. **Fleet:** capacity is a count per car type in the config file (`application.yml`). There are no individual car models or plates.
2. **Cancel:** `DELETE /reservations/{id}` soft-cancels. It sets status `CANCELLED`, keeps the record and is idempotent.
3. **Time:** the API accepts and returns UTC instants only.
4. **Limits:** maximum rental is 30 days. Default fleet is 5 sedans, 3 SUVs and 2 vans.
5. **Package:** `com.carrental`.
