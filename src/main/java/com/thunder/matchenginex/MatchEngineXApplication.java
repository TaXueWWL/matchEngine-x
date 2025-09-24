package com.thunder.matchenginex;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MatchEngineXApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatchEngineXApplication.class, args);
    }

}
