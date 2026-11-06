package com.sanaa.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the API gateway.
 *
 * <p>WHY: the gateway is a reactive (WebFlux) application, so it must not use servlet-based starters.
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
