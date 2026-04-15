package dev.noobth.pulsebackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class PulseBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(PulseBackendApplication.class, args);
    }

}
