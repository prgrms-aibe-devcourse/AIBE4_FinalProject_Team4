package kr.java.documind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class Aibe4FinalProjectTeam4Application {

    public static void main(String[] args) {
        SpringApplication.run(Aibe4FinalProjectTeam4Application.class, args);
    }
}
