package com.example.ride_service;

import org.springframework.boot.SpringApplication;

public class TestRideServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(RideServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
