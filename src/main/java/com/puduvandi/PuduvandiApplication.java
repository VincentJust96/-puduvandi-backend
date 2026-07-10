package com.puduvandi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Puduvandi - Tourism Mobility Marketplace
 * <p>
 * Entry point for the Spring Boot application.
 * Modular Monolith architecture with future-ready design.
 */
@SpringBootApplication
@EnableScheduling
public class PuduvandiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PuduvandiApplication.class, args);
    }
}
