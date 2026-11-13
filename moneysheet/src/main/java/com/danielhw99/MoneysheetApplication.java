package com.danielhw99;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude=UserDetailsServiceAutoConfiguration.class)
public class MoneysheetApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoneysheetApplication.class, args);
    }
}
