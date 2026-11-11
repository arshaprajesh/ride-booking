
package com.example.ride_service.service;

import com.example.contracts.ride.RideRequestedEvent;
import com.example.ride_service.dto.CreateRideRequest;
import com.example.ride_service.dto.RideResponse;
import com.example.ride_service.entity.Customer;
import com.example.ride_service.entity.OutboxEvent;
import com.example.ride_service.entity.Ride;
import com.example.ride_service.repository.CustomerRepository;
import com.example.ride_service.repository.OutboxEventRepository;
import com.example.ride_service.repository.RideRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.core.JacksonException;//JacksonException represents errors during JSON processing.
import tools.jackson.databind.json.JsonMapper;//JsonMapper converts Java objects into JSON.
import org.slf4j.Logger;
import java.time.Instant;//Represents a timestamp
import java.util.UUID;

@Service
public class RideCreationTransaction {
    private static final Logger log =
            org.slf4j.LoggerFactory.getLogger(RideCreationTransaction.class);
    private final CustomerRepository customerRepository;
    private final RideRepository rideRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper jsonMapper;

    public RideCreationTransaction(
            CustomerRepository customerRepository,
            RideRepository rideRepository,
            OutboxEventRepository outboxEventRepository,
            JsonMapper jsonMapper) {

        this.customerRepository = customerRepository;
        this.rideRepository = rideRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional// This tells Spring to execute the database operations inside one transaction.
    public RideResponse createRide(
            CreateRideRequest request,
            String idempotencyKey,
            String requestHash) {
        log.info(
                "Starting ride creation transaction. customerId={}, idempotencyKey={}",
                request.customerId(),
                idempotencyKey
        );
        // 1. Find customer.
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> {
                    log.warn(
                            "Customer not found. customerId={}",
                            request.customerId()
                    );

                    return new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Customer not found"
                    );
                });

        log.debug(
                "Customer found. customerId={}",
                customer.getId()
        );

        // 2. Create ride.
        UUID rideId = UUID.randomUUID();

        Ride ride = new Ride(
                rideId,
                customer,
                request.pickupLatitude(),
                request.pickupLongitude(),
                request.dropLatitude(),
                request.dropLongitude(),
                idempotencyKey,
                requestHash
        );

        // Flush immediately so a duplicate-key violation occurs here,
        // inside this transaction.
        rideRepository.saveAndFlush(ride);
        log.info(
                "Ride saved to database. rideId={}, status={}",
                ride.getId(),
                ride.getStatus()
        );
        // 3. Create ride.requested event.
        RideRequestedEvent event = new RideRequestedEvent(
                UUID.randomUUID(),
                1,
                rideId,
                customer.getId(),
                request.pickupLatitude(),
                request.pickupLongitude(),
                request.dropLatitude(),
                request.dropLongitude(),
                Instant.now()
        );
         // 4. Convert event to JSON.
        String payload;

        try {
            payload = jsonMapper.writeValueAsString(event);//Convert Java object to JSON(Kafka messages need a serialized representation of the event)
            log.debug(
                    "Ride event serialized successfully. rideId={}",
                    rideId
            );
        } catch (JacksonException exception) {
            log.error(
                    "Failed to serialize ride event. rideId={}",
                    rideId,
                    exception
            );
            throw new IllegalStateException(
                    "Could not serialize ride event",
                    exception
            );
        }
        // 5. Save event in outbox table.
        OutboxEvent outboxEvent = new OutboxEvent(
                UUID.randomUUID(),
                "RIDE",
                rideId,
                "ride.requested",
                payload
        );

        outboxEventRepository.save(outboxEvent);
        log.info(
                "Outbox event saved. rideId={}, eventType={}",
                rideId,
                "ride.requested"
        );
        // 6. Return response.
        RideResponse response = new RideResponse(
                ride.getId(),
                customer.getId(),
                ride.getStatus(),
                ride.getPickupLatitude(),
                ride.getPickupLongitude(),
                ride.getDropLatitude(),
                ride.getDropLongitude(),
                ride.getCreatedAt()
        );
        log.info(
                "Ride creation transaction completed. rideId={}",
                rideId
        );

        return response;
    }
}
