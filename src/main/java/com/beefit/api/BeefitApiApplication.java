package com.beefit.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BeefitApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(BeefitApiApplication.class, args);
	}

}
