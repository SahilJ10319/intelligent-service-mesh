package com.neuragate;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

// Main entry point for NeuraGate
@SpringBootApplication
public class NeuraGateApplication {

    public static void main(String[] args) {
        SpringApplication.run(NeuraGateApplication.class, args);
    }

    @Bean
    public CommandLineRunner startupLogger() {
        return args -> {
            System.out.println("Virtual Threads Enabled: true");
        };
    }
}
