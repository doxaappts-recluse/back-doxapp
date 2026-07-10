package pe.dcs.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.ZoneId;

@SpringBootApplication
@EnableScheduling
public class AppApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppApplication.class, args);
        System.out.println("Zona JVM: " + ZoneId.systemDefault());

    }
}