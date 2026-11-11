package com.example.ride_service.dto;

import com.example.ride_service.entity.RideStatus;

import java.time.Instant;
import java.util.UUID;

public record RideResponse(
        UUID rideId,
        UUID customerId,
        RideStatus status,
        double pickupLatitude,
        double pickupLongitude,
        double dropLatitude,
        double dropLongitude,
        Instant createdAt
) {}