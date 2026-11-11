
package com.example.ride_service.repository;

import com.example.ride_service.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;


public interface CustomerRepository
        extends JpaRepository<Customer, UUID> {
}
