package com.ponomarenko.mugloar.domain;

import com.ponomarenko.mugloar.client.dto.GameStartResponse;
import com.ponomarenko.mugloar.client.dto.PurchaseResponse;
import com.ponomarenko.mugloar.client.dto.SolveResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameStateTest {

	@Test
	void isBuiltFromStartResponse() {
		GameState state = GameState.from(new GameStartResponse("g1", 3, 0, 0, 0, 500, 0));

		assertThat(state.gameId()).isEqualTo("g1");
		assertThat(state.lives()).isEqualTo(3);
		assertThat(state.highScore()).isEqualTo(500);
		assertThat(state.isOver()).isFalse();
	}

	@Test
	void solveResponseUpdatesEverythingButLevel() {
		GameState before = new GameState("g1", 3, 10, 2, 100, 500, 4);

		GameState after = before.after(new SolveResponse(true, 3, 60, 150, 500, 5, "Solved"));

		assertThat(after.gold()).isEqualTo(60);
		assertThat(after.score()).isEqualTo(150);
		assertThat(after.turn()).isEqualTo(5);
		assertThat(after.level()).isEqualTo(2);
		assertThat(after.gameId()).isEqualTo("g1");
	}

	@Test
	void purchaseResponseUpdatesEverythingButScore() {
		GameState before = new GameState("g1", 3, 200, 0, 100, 500, 4);

		GameState after = before.after(new PurchaseResponse(true, 100, 3, 1, 5));

		assertThat(after.gold()).isEqualTo(100);
		assertThat(after.level()).isEqualTo(1);
		assertThat(after.turn()).isEqualTo(5);
		assertThat(after.score()).isEqualTo(100);
		assertThat(after.highScore()).isEqualTo(500);
	}

	@Test
	void gameIsOverWhenNoLivesRemain() {
		assertThat(new GameState("g1", 0, 0, 0, 0, 0, 0).isOver()).isTrue();
		assertThat(new GameState("g1", 1, 0, 0, 0, 0, 0).isOver()).isFalse();
	}
}
