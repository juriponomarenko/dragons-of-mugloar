package com.ponomarenko.mugloar.e2e;

import com.ponomarenko.mugloar.MugloarBackendApplication;
import com.ponomarenko.mugloar.client.MugloarApiClient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Starts the real application on the test classpath with {@link StubMugloarApi} in place of the
 * HTTP client, for the frontend's Playwright tests:
 *
 * <pre>
 * ./mvnw spring-boot:test-run
 * </pre>
 *
 * The {@code e2e} profile moves the server to a port of its own and shortens a server-side run, see
 * {@code application-e2e.properties}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class E2eBackend {

	public static void main(String[] args) {
		SpringApplication.from(MugloarBackendApplication::main)
			.with(E2eBackend.class)
			.withAdditionalProfiles("e2e")
			.run(args);
	}

	@Bean
	@Primary
	MugloarApiClient stubMugloarApi() {
		return new StubMugloarApi();
	}

}
