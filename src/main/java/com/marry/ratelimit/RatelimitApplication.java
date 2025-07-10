package com.marry.ratelimit;

import com.marry.ratelimit.starter.annotation.EnableRateLimit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableRateLimit
public class RatelimitApplication {

    public static void main(String[] args) {
        SpringApplication.run(RatelimitApplication.class, args);
    }

}
