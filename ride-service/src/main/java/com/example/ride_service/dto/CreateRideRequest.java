package com.example.ride_service.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;


import java.util.UUID;

public record CreateRideRequest(

        @NotNull
        UUID customerId,

        @NotNull
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        Double pickupLatitude,

        @NotNull
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        Double pickupLongitude,

        @NotNull
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        Double dropLatitude,

        @NotNull
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        Double dropLongitude

) {}