package dev.sandytang.flashdeal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class FlashDealApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlashDealApplication.class, args);
    }
}
