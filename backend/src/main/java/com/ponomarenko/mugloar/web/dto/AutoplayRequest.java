package com.ponomarenko.mugloar.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Optional body of an autoplay request.
 *
 * @param maxTurns upper bound on the number of actions to take; defaults to playing until the game ends
 */
public record AutoplayRequest(@Min(1) @Max(5000) Integer maxTurns) {

	public static final int DEFAULT_MAX_TURNS = 5000;

	public int maxTurnsOrDefault() {
		return maxTurns != null ? maxTurns : DEFAULT_MAX_TURNS;
	}
}
