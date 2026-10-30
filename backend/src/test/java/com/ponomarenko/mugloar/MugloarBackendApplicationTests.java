package com.ponomarenko.mugloar;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.autoconfigure.HttpClientsProperties;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MugloarBackendApplicationTests {

	@Autowired
	private HttpClientsProperties httpClients;

	@Test
	void contextLoads() {
	}

	/** The property keys changed in Boot 4 (spring.http.clients.*); a wrong key would leave no timeout at all. */
	@Test
	void theConfiguredHttpTimeoutsAreBound() {
		assertThat(httpClients.getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
		assertThat(httpClients.getReadTimeout()).isEqualTo(Duration.ofSeconds(2));
	}

}
