
package com.example.ride_service.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rides")
public class Ride {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "pickup_latitude", nullable = false)
    private double pickupLatitude;

    @Column(name = "pickup_longitude", nullable = false)
    private double pickupLongitude;

    @Column(name = "drop_latitude", nullable = false)
    private double dropLatitude;

    @Column(name = "drop_longitude", nullable = false)
    private double dropLongitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RideStatus status;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Generated(event = EventType.INSERT)
    @Column(
        name = "created_at",
        nullable = false,
        insertable = false,
        updatable = false
    )
    private Instant createdAt;

    protected Ride() {
    }

    public Ride(
            UUID id,
            Customer customer,
            double pickupLatitude,
            double pickupLongitude,
            double dropLatitude,
            double dropLongitude,
            String idempotencyKey,
            String requestHash
    ) {
        this.id = id;
        this.customer = customer;
        this.pickupLatitude = pickupLatitude;
        this.pickupLongitude = pickupLongitude;
        this.dropLatitude = dropLatitude;
        this.dropLongitude = dropLongitude;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.status = RideStatus.REQUESTED;
    }

    public UUID getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public double getPickupLatitude() {
        return pickupLatitude;
    }

    public double getPickupLongitude() {
        return pickupLongitude;
    }

    public double getDropLatitude() {
        return dropLatitude;
    }

    public double getDropLongitude() {
        return dropLongitude;
    }

    public RideStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
