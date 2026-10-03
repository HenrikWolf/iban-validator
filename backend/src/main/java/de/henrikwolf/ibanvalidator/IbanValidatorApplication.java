package de.henrikwolf.ibanvalidator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IbanValidatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(IbanValidatorApplication.class, args);
    }

}
