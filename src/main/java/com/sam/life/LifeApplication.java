package com.sam.life;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;

@SpringBootApplication
public class LifeApplication {

    @Autowired
    private Environment environment;

    public static void main(String[] args) {
        SpringApplication.run(LifeApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void printUploadUrl() {
        String port = environment.getProperty("server.port", "8080");
        System.out.println("\n==============================================");
        System.out.println("Application is ready!");
        System.out.println("Upload page: http://localhost:" + port);
        System.out.println("==============================================\n");
    }

}
