package com.carrental.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * End-to-end rental scenarios through the REST API.
 * Fleet: 2 sedans, 2 SUVs, 1 van. "Now" is fixed at 2026-10-01T00:00:00Z. Each test gets a fresh data file.
 */
@SpringBootTest(properties = {
        "carrental.max-rental-days=30",
        "carrental.fleet.SEDAN=2",
        "carrental.fleet.SUV=2",
        "carrental.fleet.VAN=1"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ReservationScenariosTest {

    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
    private static final String MON_10AM = "2026-11-02T10:00:00Z";
    private static final String THU_10AM = "2026-11-05T10:00:00Z";

    private static final Path DATA_DIR = createDataDir();

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @DynamicPropertySource
    static void dataFile(DynamicPropertyRegistry registry) {
        registry.add("carrental.data-file", () -> DATA_DIR.resolve(UUID.randomUUID() + ".json").toString());
    }

    @Autowired
    MockMvc mvc;

    private static Path createDataDir() {
        try {
            Path dir = Files.createTempDirectory("carrental-test");
            dir.toFile().deleteOnExit();
            return dir;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private ResultActions reserve(String type, String start, int days) throws Exception {
        String body = """
                {"customerName":"Ana Silva","carType":"%s","startTime":"%s","days":%d}"""
                .formatted(type, start, days);
        return mvc.perform(post("/api/v1/reservations").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String reserveOk(String type, String start, int days) throws Exception {
        String json = reserve(type, start, days).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private ResultActions availability(String type, String start, int days) throws Exception {
        return mvc.perform(get("/api/v1/availability")
                .param("carType", type).param("start", start).param("days", String.valueOf(days)));
    }

    // 1
    @Test
    void reserveAvailableSedan() throws Exception {
        reserve("SEDAN", MON_10AM, 3)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/reservations/")))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.carType").value("SEDAN"))
                .andExpect(jsonPath("$.startTime").value(MON_10AM))
                .andExpect(jsonPath("$.endTime").value(THU_10AM))
                .andExpect(jsonPath("$.days").value(3));
    }

    // 2
    @Test
    void reserveEachCarType() throws Exception {
        reserveOk("SEDAN", MON_10AM, 1);
        reserveOk("SUV", MON_10AM, 1);
        reserveOk("VAN", MON_10AM, 1);
    }

    // 3
    @Test
    void rejectsWhenTypeIsFull() throws Exception {
        reserveOk("SEDAN", MON_10AM, 3);
        reserveOk("SEDAN", MON_10AM, 3);

        reserve("SEDAN", MON_10AM, 3)
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.detail", containsString("No SEDAN available")));
    }

    // 4
    @Test
    void fullTypeDoesNotBlockOtherTypes() throws Exception {
        reserveOk("VAN", MON_10AM, 3);
        reserve("VAN", MON_10AM, 3).andExpect(status().isConflict());

        reserveOk("SUV", MON_10AM, 3);
        reserveOk("SEDAN", MON_10AM, 3);
    }

    // 5
    @Test
    void sameTypeNonOverlappingDatesBothSucceed() throws Exception {
        reserveOk("VAN", MON_10AM, 2);
        reserveOk("VAN", "2026-11-10T10:00:00Z", 2);
    }

    // 6
    @Test
    void backToBackRentalsSucceed() throws Exception {
        reserveOk("VAN", MON_10AM, 3);
        reserveOk("VAN", THU_10AM, 2);
    }

    // 7
    @Test
    void oneHourOverlapOnLastCarIsRejected() throws Exception {
        reserveOk("VAN", MON_10AM, 3);

        reserve("VAN", "2026-11-05T09:00:00Z", 1).andExpect(status().isConflict());
    }

    // 8
    @Test
    void longReservationSpanningShortOneCountsOnce() throws Exception {
        reserveOk("SUV", "2026-11-03T10:00:00Z", 1);
        reserveOk("SUV", MON_10AM, 10);

        reserve("SUV", "2026-11-03T12:00:00Z", 1).andExpect(status().isConflict());
        reserveOk("SUV", "2026-11-04T10:00:00Z", 1);
    }

    // 9
    @Test
    void cancelFreesCarForRebooking() throws Exception {
        String id = reserveOk("VAN", MON_10AM, 3);
        reserve("VAN", MON_10AM, 3).andExpect(status().isConflict());

        mvc.perform(delete("/api/v1/reservations/" + id)).andExpect(status().isNoContent());

        reserveOk("VAN", MON_10AM, 3);
    }

    // 10
    @Test
    void cancelTwiceIsIdempotentAndUnknownIdIsNotFound() throws Exception {
        String id = reserveOk("SEDAN", MON_10AM, 1);

        mvc.perform(delete("/api/v1/reservations/" + id)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/reservations/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/reservations/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mvc.perform(delete("/api/v1/reservations/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    // 11
    @Test
    void availabilityReflectsBookings() throws Exception {
        availability("SEDAN", MON_10AM, 3)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(2))
                .andExpect(jsonPath("$.available").value(2));

        String id = reserveOk("SEDAN", MON_10AM, 3);
        availability("SEDAN", MON_10AM, 3)
                .andExpect(jsonPath("$.reserved").value(1))
                .andExpect(jsonPath("$.available").value(1));

        mvc.perform(delete("/api/v1/reservations/" + id)).andExpect(status().isNoContent());
        availability("SEDAN", MON_10AM, 3).andExpect(jsonPath("$.available").value(2));
    }

    // 12
    @Test
    void listAndGetReservations() throws Exception {
        String sedanId = reserveOk("SEDAN", MON_10AM, 1);
        String suvId = reserveOk("SUV", MON_10AM, 1);
        mvc.perform(delete("/api/v1/reservations/" + suvId)).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/reservations")).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/v1/reservations").param("carType", "SEDAN"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(sedanId));
        mvc.perform(get("/api/v1/reservations").param("status", "CANCELLED"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(suvId));
        mvc.perform(get("/api/v1/reservations/" + sedanId)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/reservations/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void listCarTypesWithCapacity() throws Exception {
        mvc.perform(get("/api/v1/car-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[?(@.carType=='SEDAN')].capacity").value(2))
                .andExpect(jsonPath("$[?(@.carType=='VAN')].capacity").value(1));
    }

    // Validation

    @Test
    void rejectsStartInThePast() throws Exception {
        reserve("SEDAN", "2026-09-30T23:59:59Z", 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("past")));
    }

    @Test
    void rejectsDaysOutOfRange() throws Exception {
        reserve("SEDAN", MON_10AM, 0).andExpect(status().isBadRequest());
        reserve("SEDAN", MON_10AM, -2).andExpect(status().isBadRequest());
        reserve("SEDAN", MON_10AM, 31).andExpect(status().isBadRequest());
        reserveOk("SEDAN", MON_10AM, 30);
    }

    @Test
    void rejectsUnknownCarTypeAndMalformedDate() throws Exception {
        reserve("TRUCK", MON_10AM, 1).andExpect(status().isBadRequest());
        reserve("SEDAN", "next tuesday", 1).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMissingFieldsWithFieldDetails() throws Exception {
        mvc.perform(post("/api/v1/reservations").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.customerName").exists())
                .andExpect(jsonPath("$.errors.carType").exists())
                .andExpect(jsonPath("$.errors.startTime").exists())
                .andExpect(jsonPath("$.errors.days").exists());
    }

    @Test
    void rejectsBlankCustomerName() throws Exception {
        String body = """
                {"customerName":"  ","carType":"SEDAN","startTime":"%s","days":1}""".formatted(MON_10AM);
        mvc.perform(post("/api/v1/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.customerName").exists());
    }

    @Test
    void availabilityRejectsBadParameters() throws Exception {
        availability("TRUCK", MON_10AM, 1).andExpect(status().isBadRequest());
        availability("SEDAN", MON_10AM, 99).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/availability").param("carType", "SEDAN")).andExpect(status().isBadRequest());
    }
}
