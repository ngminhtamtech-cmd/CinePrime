package vn.cineprime;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CinePrimeApplication {
    public static void main(String[] args) { SpringApplication.run(CinePrimeApplication.class, args); }
    @Bean Clock clock() { return Clock.systemUTC(); }
}
