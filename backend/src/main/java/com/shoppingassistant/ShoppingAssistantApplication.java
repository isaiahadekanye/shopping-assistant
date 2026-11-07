package com.shoppingassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ShoppingAssistantApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShoppingAssistantApplication.class, args);
    }
}
