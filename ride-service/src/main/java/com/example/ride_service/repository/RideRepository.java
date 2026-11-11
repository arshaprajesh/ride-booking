
package com.example.ride_service.repository;

import com.example.ride_service.entity.Ride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RideRepository
        extends JpaRepository<Ride, UUID> {

    Optional<Ride> findByIdempotencyKey(String idempotencyKey);
}
