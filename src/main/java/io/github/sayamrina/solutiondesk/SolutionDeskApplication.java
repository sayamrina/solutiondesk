package io.github.sayamrina.solutiondesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SolutionDeskApplication {

    public static void main(String[] args) {
        SpringApplication.run(SolutionDeskApplication.class, args);
    }
}
