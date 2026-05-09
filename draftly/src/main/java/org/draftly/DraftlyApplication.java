package org.draftly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"org.config", "org.controller", "org.service", "org.client", "org.util"})
@EnableJpaRepositories(basePackages = "org.repository")
@EntityScan(basePackages = "org.entity")
public class DraftlyApplication {

    public static void main(String[] args) {
        SpringApplication.run(DraftlyApplication.class, args);
    }
}
