package com.ponomarenko.mugloar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MugloarBackendApplication {

	/** Profile in which the application plays games from the command line instead of serving the API. */
	static final String AUTOPLAY_PROFILE = "autoplay";

	public static void main(String[] args) {
		ConfigurableApplicationContext context = SpringApplication.run(MugloarBackendApplication.class, args);
		// In CLI mode there is no web server to keep the JVM alive and the runner's exit code (non-zero
		// when a game missed the target) only reaches the shell when the context is closed through exit().
		if (context.getEnvironment().matchesProfiles(AUTOPLAY_PROFILE)) {
			System.exit(SpringApplication.exit(context));
		}
	}

}
