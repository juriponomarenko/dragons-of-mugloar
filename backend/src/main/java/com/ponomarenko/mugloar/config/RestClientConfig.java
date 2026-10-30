package com.ponomarenko.mugloar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

	/**
	 * {@link RestClient} pointed at the Mugloar API. Connection and read timeouts come from
	 * Boot's {@code spring.http.clients.*} properties, which the auto-configured builder already
	 * applies, so a slow upstream can never hang a request indefinitely.
	 */
	@Bean
	RestClient mugloarApiRestClient(RestClient.Builder builder, MugloarApiProperties properties) {
		return builder
				.baseUrl(properties.baseUrl())
				.defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
				.defaultHeader(HttpHeaders.ACCEPT, "application/json")
				.build();
	}
}
