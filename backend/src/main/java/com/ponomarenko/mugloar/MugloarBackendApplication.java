package com.ponomarenko.mugloar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MugloarBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(MugloarBackendApplication.class, args);
	}

}
