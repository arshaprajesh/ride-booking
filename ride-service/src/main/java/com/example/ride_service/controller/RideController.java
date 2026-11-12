package com.example.ride_service.controller;

import com.example.ride_service.dto.CreateRideRequest;
import com.example.ride_service.dto.RideResponse;
import com.example.ride_service.service.RideService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/rides")
public class RideController {
    private static final Logger log =
            LoggerFactory.getLogger(RideController.class);
    private final RideService rideService;

    public RideController(RideService rideService) {
        this.rideService = rideService;
    }

    @PostMapping
    public ResponseEntity<RideResponse> createRide(
            @Valid @RequestBody CreateRideRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        log.info("Received ride creation request");
        RideResponse response =
                rideService.createRide(request, idempotencyKey);
        log.info(
                "Ride creation request completed. rideId={}",
                response.rideId()
        );


        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{rideId}")
    public ResponseEntity<RideResponse> getRideById(
            @PathVariable java.util.UUID rideId) {

        RideResponse response = rideService.getRideById(rideId);

        return ResponseEntity.ok(response);
    }
}