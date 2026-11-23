package com.mehanika.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MehanikaBotApplication {
    public static void main(String[] args) {
        for (String arg : args) {
            if ("--self-test=start".equals(arg)) {
                System.exit(StartSelfTest.run());
            }
        }
        SpringApplication.run(MehanikaBotApplication.class, args);
    }
}
