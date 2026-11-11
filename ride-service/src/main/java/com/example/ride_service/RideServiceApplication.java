package com.example.ride_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication //@SpringBootApplication starts Spring Boot and component scanning.
@EnableScheduling //enables scheduled methods such as our Outbox Publisher.

public class RideServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(RideServiceApplication.class, args);
	}

}
