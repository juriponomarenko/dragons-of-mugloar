package com.ponomarenko.mugloar.service;

import java.util.List;

import com.ponomarenko.mugloar.domain.GameState;

/**
 * Result of an autoplay run.
 *
 * @param finalState state when the run stopped
 * @param turns the actions taken during this run (not the whole game history)
 * @param gameOver whether the game ended during the run
 * @param stoppedBecause why the run stopped
 */
public record AutoplayOutcome(GameState finalState, List<TurnRecord> turns, boolean gameOver, String stoppedBecause) {
}
