
package com.example.ride_service;

import com.example.ride_service.dto.CreateRideRequest;
import com.example.ride_service.dto.RideResponse;
import com.example.ride_service.outbox.OutboxPublisher;
import com.example.ride_service.service.RideService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RideServiceIntegrationTest {

    private static final String CUSTOMER_ID =
            "66666666-6666-6666-6666-666666666666";

    @Autowired
    private RideService rideService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Prevent scheduled Kafka publisher from running during tests.
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
                UUID.fromString(CUSTOMER_ID),
                "Arun Kumar",
                "arun.test@example.com",
                "9195550106"
        );
    }

    // TEST 1: New request creates one ride and one outbox event.

    @Test
    void shouldCreateNewRide() {

        CreateRideRequest request = createRequest(35.7915);

        RideResponse response =
                rideService.createRide(request, "test-key-001");

        assertNotNull(response.rideId());
        assertEquals(CUSTOMER_ID, response.customerId().toString());
        assertEquals("REQUESTED", response.status().name());

        assertEquals(1L, countRides());
        assertEquals(1L, countOutboxEvents());
    }

    // TEST 2: Same key and same data returns existing ride.

    @Test
    void shouldReturnExistingRideForSameIdempotencyKey() {

        CreateRideRequest request = createRequest(35.7915);

        RideResponse first =
                rideService.createRide(request, "test-key-002");

        RideResponse second =
                rideService.createRide(request, "test-key-002");

        assertEquals(first.rideId(), second.rideId());

        assertEquals(1L, countRides());
        assertEquals(1L, countOutboxEvents());
    }

    // TEST 3: Same key with different data returns HTTP 409.

    @Test
    void shouldReturnConflictForDifferentRequestData() {

        CreateRideRequest firstRequest = createRequest(35.7915);
        CreateRideRequest changedRequest = createRequest(35.8000);

        rideService.createRide(firstRequest, "test-key-003");

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> rideService.createRide(
                                changedRequest,
                                "test-key-003"
                        )
                );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        assertEquals(1L, countRides());
        assertEquals(1L, countOutboxEvents());
    }

    // TEST 4: Concurrent identical requests create only one ride.

    @Test
    void shouldCreateOnlyOneRideForConcurrentIdenticalRequests()
            throws Exception {

        CreateRideRequest request = createRequest(35.7915);

        CountDownLatch startSignal = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {

            Future<RideResponse> first = executor.submit(() -> {
                startSignal.await();
                return rideService.createRide(request, "test-key-004");
            });

            Future<RideResponse> second = executor.submit(() -> {
                startSignal.await();
                return rideService.createRide(request, "test-key-004");
            });

            startSignal.countDown();

            RideResponse firstResponse =
                    first.get(30, TimeUnit.SECONDS);

            RideResponse secondResponse =
                    second.get(30, TimeUnit.SECONDS);

            assertEquals(
                    firstResponse.rideId(),
                    secondResponse.rideId()
            );

            assertEquals(1L, countRides());
            assertEquals(1L, countOutboxEvents());

        } finally {
            executor.shutdownNow();
        }
    }

    // TEST 5: Concurrent different requests produce one success
    // and one HTTP 409 Conflict.

    @Test
    void shouldReturnOneSuccessAndOneConflictForConcurrentDifferentRequests()
            throws Exception {

        CreateRideRequest firstRequest = createRequest(35.7915);
        CreateRideRequest secondRequest = createRequest(35.8000);

        CountDownLatch startSignal = new CountDownLatch(1);

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {

            Future<?> first = executor.submit(() -> {
                startSignal.await();

                try {
                    rideService.createRide(
                            firstRequest,
                            "test-key-005"
                    );

                    successes.incrementAndGet();

                } catch (ResponseStatusException exception) {

                    if (exception.getStatusCode() == HttpStatus.CONFLICT) {
                        conflicts.incrementAndGet();
                    } else {
                        throw exception;
                    }
                }

                return null;
            });

            Future<?> second = executor.submit(() -> {
                startSignal.await();

                try {
                    rideService.createRide(
                            secondRequest,
                            "test-key-005"
                    );

                    successes.incrementAndGet();

                } catch (ResponseStatusException exception) {

                    if (exception.getStatusCode() == HttpStatus.CONFLICT) {
                        conflicts.incrementAndGet();
                    } else {
                        throw exception;
                    }
                }

                return null;
            });

            startSignal.countDown();

            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);

            assertEquals(1, successes.get());
            assertEquals(1, conflicts.get());

            assertEquals(1L, countRides());
            assertEquals(1L, countOutboxEvents());

        } finally {
            executor.shutdownNow();
        }
    }

    private CreateRideRequest createRequest(double dropLatitude) {

        return new CreateRideRequest(
                UUID.fromString(CUSTOMER_ID),
                35.7796,
                -78.6382,
                dropLatitude,
                -78.7811
        );
    }

    private long countRides() {

        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rides",
                Long.class
        );
    }

    private long countOutboxEvents() {

        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events",
                Long.class
        );
    }
}
