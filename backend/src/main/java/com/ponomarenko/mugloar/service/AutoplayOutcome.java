package com.ponomarenko.mugloar.service;

import java.util.List;

import com.ponomarenko.mugloar.domain.GameState;

/**
 * Result of an autoplay run.
 *
 * @param finalSnapshot state and purchases when the run stopped, taken while the run still held
 * the session lock, so a response built from it cannot mix in a later action
 * @param turns the actions taken during this run (not the whole game history)
 * @param gameOver whether the game ended during the run
 * @param stoppedBecause why the run stopped
 */
public record AutoplayOutcome(GameSession.Snapshot finalSnapshot, List<TurnRecord> turns, boolean gameOver,
		String stoppedBecause) {

	public GameState finalState() {
		return finalSnapshot.state();
	}
}
