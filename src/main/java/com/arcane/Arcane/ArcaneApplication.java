package com.arcane.Arcane;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ArcaneApplication {

	public static void main(String[] args) {
		SpringApplication.run(ArcaneApplication.class, args);
	}

}
