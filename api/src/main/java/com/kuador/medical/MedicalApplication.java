package com.kuador.medical;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Medical Kuador api: a modular monolith (ADR-001) whose modules are the direct
 * sub-packages of this one.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MedicalApplication {

    public static void main(String[] args) {
        SpringApplication.run(MedicalApplication.class, args);
    }
}
