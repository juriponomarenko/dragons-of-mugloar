package com.ponomarenko.mugloar.service;

import java.time.Instant;

import com.ponomarenko.mugloar.domain.GameState;

/**
 * One action taken in a game and its outcome.
 *
 * @param turn turn number reported by the API after the action
 * @param action what was done
 * @param targetId the ad or item id acted on
 * @param description the ad text or item name
 * @param success whether the ad was solved / the purchase went through
 * @param message explanation returned by the API (solve only)
 * @param reason why the strategy chose this action; empty for manual actions
 * @param automated whether the action was chosen by the autoplayer
 * @param stateAfter game state after the action
 * @param at when the action completed
 */
public record TurnRecord(int turn, ActionType action, String targetId, String description, boolean success,
		String message, String reason, boolean automated, GameState stateAfter, Instant at) {
}
