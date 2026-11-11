
package com.example.ride_service;

import com.example.ride_service.outbox.OutboxPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RideControllerIntegrationTest {

    private static final UUID CUSTOMER_ID =
            UUID.fromString("66666666-6666-6666-6666-666666666666");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Prevent the scheduled publisher from running during these tests.
    @MockitoBean
    private OutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM outbox_events");
        jdbcTemplate.execute("DELETE FROM rides");
        jdbcTemplate.execute("DELETE FROM customers");

        jdbcTemplate.update("""
                INSERT INTO customers (id, name, email, phone)
                VALUES (?, ?, ?, ?)
                """,
                CUSTOMER_ID,
                "Arun Kumar",
                "arun.test@example.com",
                "9195550106"
        );
    }

    @Test
    void shouldReturn201WhenRideIsCreated() throws Exception {

        mockMvc.perform(post("/rides")
                        .header("Idempotency-Key", "api-test-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(35.7915)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rideId").exists())
                .andExpect(jsonPath("$.customerId")
                        .value(CUSTOMER_ID.toString()))
                .andExpect(jsonPath("$.status").value("REQUESTED"));

        assertDatabaseCounts(1, 1);
    }

    @Test
    void shouldReturn201ForSameRequestWithSameIdempotencyKey()
            throws Exception {

        // First HTTP request creates the ride.
        mockMvc.perform(post("/rides")
                        .header("Idempotency-Key", "api-test-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(35.7915)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rideId").exists());

        // Second identical HTTP request returns the existing ride.
        mockMvc.perform(post("/rides")
                        .header("Idempotency-Key", "api-test-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(35.7915)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rideId").exists())
                .andExpect(jsonPath("$.status").value("REQUESTED"));

        // Only one ride and one outbox event should exist.
        assertDatabaseCounts(1, 1);
    }

    @Test
    void shouldReturn409WhenSameKeyIsUsedWithDifferentRequestData()
            throws Exception {

        // First request creates a ride.
        mockMvc.perform(post("/rides")
                        .header("Idempotency-Key", "api-test-003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(35.7915)))
                .andExpect(status().isCreated());

        // Second request uses the same key but changes dropLatitude.
        mockMvc.perform(post("/rides")
                        .header("Idempotency-Key", "api-test-003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(35.8000)))
                .andExpect(status().isConflict());

        // The conflicting request must not create another ride or event.
        assertDatabaseCounts(1, 1);
    }

    @Test
    void shouldReturn200WhenRideExists() throws Exception {

        // Create a ride first.
        mockMvc.perform(post("/rides")
                        .header("Idempotency-Key", "api-get-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(35.7915)))
                .andExpect(status().isCreated());

        // Get the generated ride ID.
        UUID rideId = jdbcTemplate.queryForObject(
                "SELECT id FROM rides WHERE idempotency_key = ?",
                UUID.class,
                "api-get-001"
        );

        // Retrieve the ride.
        mockMvc.perform(get("/rides/{rideId}", rideId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rideId").value(rideId.toString()))
                .andExpect(jsonPath("$.customerId")
                        .value(CUSTOMER_ID.toString()))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.pickupLatitude").value(35.7796))
                .andExpect(jsonPath("$.dropLatitude").value(35.7915))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    private String createRequestJson(double dropLatitude) {
        return """
                {
                    "customerId": "%s",
                    "pickupLatitude": 35.7796,
                    "pickupLongitude": -78.6382,
                    "dropLatitude": %s,
                    "dropLongitude": -78.7811
                }
                """.formatted(CUSTOMER_ID, dropLatitude);
    }
    @Test
    void shouldReturn404WhenRideDoesNotExist() throws Exception {

        UUID unknownRideId = UUID.fromString(
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
        );

        mockMvc.perform(get("/rides/{rideId}", unknownRideId))
                .andExpect(status().isNotFound());
    }

    private void assertDatabaseCounts(
            int expectedRides,
            int expectedOutboxEvents) {

        Integer rideCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rides",
                Integer.class
        );

        Integer outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events",
                Integer.class
        );

        org.junit.jupiter.api.Assertions.assertEquals(
                expectedRides, rideCount, "Unexpected number of rides"
        );

        org.junit.jupiter.api.Assertions.assertEquals(
                expectedOutboxEvents, outboxCount,
                "Unexpected number of outbox events"
        );
    }
}
