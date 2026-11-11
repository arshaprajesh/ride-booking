package com.example.ride_service.controller;

import com.example.ride_service.entity.Customer;
import com.example.ride_service.repository.CustomerRepository;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/customers")
public class CustomerController {
    private static final Logger log =
            LoggerFactory.getLogger(CustomerController.class);

    private final CustomerRepository customerRepository;

    public CustomerController(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @GetMapping
    public List<Customer> getAllCustomers() {
        log.info("Fetching all customers");

        List<Customer> customers = customerRepository.findAll();

        log.info("Retrieved {} customers", customers.size());

        return customers;
    }

    @GetMapping("/{id}")
    public Customer getCustomerById(@PathVariable UUID id) {
        log.info("Fetching customer by ID: {}", id);
        return customerRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Customer not found. customerId={}", id);

                    return new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Customer not found"
                    );
                });
    }
}