package com.ponomarenko.mugloar.domain;

import com.ponomarenko.mugloar.client.dto.ReputationResponse;

public record Reputation(double people, double state, double underworld) {

	public static Reputation from(ReputationResponse response) {
		return new Reputation(response.people(), response.state(), response.underworld());
	}
}
