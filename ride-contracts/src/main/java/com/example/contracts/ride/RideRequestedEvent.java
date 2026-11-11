package com.example.contracts.ride;

import java.time.Instant;
import java.util.UUID;

public record RideRequestedEvent(
        UUID eventId,
        int eventVersion,
        UUID rideId,
        UUID customerId,
        double pickupLatitude,
        double pickupLongitude,
        double dropLatitude,
        double dropLongitude,
        Instant occurredAt
) {
}