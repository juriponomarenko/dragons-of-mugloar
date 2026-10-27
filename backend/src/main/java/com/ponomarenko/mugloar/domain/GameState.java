package com.ponomarenko.mugloar.domain;

import com.ponomarenko.mugloar.client.dto.GameStartResponse;
import com.ponomarenko.mugloar.client.dto.PurchaseResponse;
import com.ponomarenko.mugloar.client.dto.SolveResponse;

/**
 * Immutable snapshot of a game. New snapshots are derived from API responses; the API does
 * not return every field on every call, so missing fields are carried over.
 */
public record GameState(String gameId, int lives, int gold, int level, int score, int highScore, int turn) {

	public static GameState from(GameStartResponse response) {
		return new GameState(response.gameId(), response.lives(), response.gold(), response.level(),
				response.score(), response.highScore(), response.turn());
	}

	public GameState after(SolveResponse response) {
		return new GameState(gameId, response.lives(), response.gold(), level, response.score(),
				response.highScore(), response.turn());
	}

	public GameState after(PurchaseResponse response) {
		return new GameState(gameId, response.lives(), response.gold(), response.level(), score, highScore,
				response.turn());
	}

	public boolean isOver() {
		return lives <= 0;
	}
}
