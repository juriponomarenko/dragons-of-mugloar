package com.ponomarenko.mugloar.web.dto;

import jakarta.validation.constraints.Min;

/**
 * Optional body of an autoplay request.
 *
 * @param maxTurns upper bound on the number of actions to take in this run; when absent the
 * run goes on until the game ends or the strategy's own {@code max-turns} cap is reached
 */
public record AutoplayRequest(@Min(1) Integer maxTurns) {

	public int maxTurnsOr(int fallback) {
		return maxTurns != null ? maxTurns : fallback;
	}
}
