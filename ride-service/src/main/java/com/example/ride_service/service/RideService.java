package com.example.ride_service.service;
import com.example.ride_service.dto.CreateRideRequest;
import com.example.ride_service.dto.RideResponse;
import com.example.ride_service.entity.Ride;
import com.example.ride_service.repository.RideRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@Service
public class RideService {
    private static final Logger log =
            LoggerFactory.getLogger(RideService.class);

    private final RideCreationTransaction rideCreationTransaction;
    private final RideRepository rideRepository;



    public RideService(  //Dependency Injection
            RideRepository rideRepository,
            RideCreationTransaction rideCreationTransaction) {


        this.rideRepository = rideRepository;
        this.rideCreationTransaction = rideCreationTransaction;

    }


    public RideResponse createRide(
            CreateRideRequest request,
            String idempotencyKey) { //idempotencyKey comes from the HTTP request header:

        validateIdempotencyKey(idempotencyKey);

        String requestHash = calculateRequestHash(request); //service calculates a SHA-256 hash using the customer ID and ride coordinates.
        // 1. Check whether this request was already processed
        log.info(
                "Processing ride request"
        );

        var existingRide = rideRepository.findByIdempotencyKey(idempotencyKey);

        if (existingRide.isPresent()) {

            log.info(
                    "Existing ride found for repeated request. rideId={}",
                    existingRide.get().getId()
            );

            return resolveExistingRide(
                    existingRide.get(),
                    requestHash
            );
        }

        // 2. Try to create a new ride.
        try {

            log.info(
                    "Creating new ride for customerId={}",
                    request.customerId()
            );

            RideResponse response =
                    rideCreationTransaction.createRide(
                            request,
                            idempotencyKey,
                            requestHash
                    );

            log.info(
                    "Ride created successfully. rideId={}, status={}",
                    response.rideId(),
                    response.status()
            );

            return response;

        } catch (DataIntegrityViolationException exception) {

            // Another simultaneous request may have inserted
            // the same idempotency key first.
            log.warn(
                    "Concurrent request detected for idempotencyKey={}",
                    idempotencyKey
            );

            // The transactional service has rolled back before
            // execution reaches this point.
            var concurrentRide =
                    rideRepository.findByIdempotencyKey(idempotencyKey);

            if (concurrentRide.isPresent()) {

                log.info(
                        "Ride created by concurrent request. rideId={}",
                        concurrentRide.get().getId()
                );

                return resolveExistingRide(
                        concurrentRide.get(),
                        requestHash
                );
            }

            // This was some other database constraint violation.
            log.error(
                    "Database integrity violation while creating ride. idempotencyKey={}",
                    idempotencyKey,
                    exception
            );

            throw exception;
        }
    }

    private RideResponse resolveExistingRide(
            Ride ride,
            String requestHash) {

        if (!ride.getRequestHash().equals(requestHash)) {

            log.warn(
                    "Idempotency conflict. key was reused with different request data. rideId={}",
                    ride.getId()
            );

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency key was already used with different request data"
            );
        }

        log.info(
                "Returning existing ride for repeated request. rideId={}",
                ride.getId()
        );

        return toResponse(ride);
    }

    private void validateIdempotencyKey(String idempotencyKey) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {

            log.warn("Request rejected: Idempotency-Key header is blank");

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key header must not be blank"
            );
        }

        if (idempotencyKey.length() > 100) {

            log.warn("Request rejected: Idempotency-Key exceeds 100 characters");

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must not exceed 100 characters"
            );
        }
    }

    private String calculateRequestHash(CreateRideRequest request) {

        String requestData = String.join("|",
                request.customerId().toString(),
                Double.toString(request.pickupLatitude()),
                Double.toString(request.pickupLongitude()),
                Double.toString(request.dropLatitude()),
                Double.toString(request.dropLongitude())
        );

        try {

            java.security.MessageDigest digest =
                    java.security.MessageDigest.getInstance("SHA-256");

            byte[] hashBytes = digest.digest(
                    requestData.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );

            return java.util.HexFormat.of().formatHex(hashBytes);

        } catch (java.security.NoSuchAlgorithmException exception) {

            log.error("SHA-256 algorithm is unavailable", exception);

            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable",
                    exception
            );
        }
    }

    private RideResponse toResponse(Ride ride) {

        return new RideResponse(
                ride.getId(),
                ride.getCustomer().getId(),
                ride.getStatus(),
                ride.getPickupLatitude(),
                ride.getPickupLongitude(),
                ride.getDropLatitude(),
                ride.getDropLongitude(),
                ride.getCreatedAt()
        );
    }
    public RideResponse getRideById(UUID rideId) {

        log.info("Fetching ride details. rideId={}", rideId);

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Ride not found: " + rideId
                ));

        return toResponse(ride);
    }
}
