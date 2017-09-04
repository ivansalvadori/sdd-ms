package br.ufsc.inf.lapesd.sddms.config;

import java.io.IOException;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan("br.ufsc.inf.lapesd.sddms")
public class SddmsApp {

    public static void main(String[] args) throws IOException {
        SpringApplication.run(SddmsApp.class, args);
    }
}
