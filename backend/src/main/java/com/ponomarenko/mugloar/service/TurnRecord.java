package com.ponomarenko.mugloar.service;

import java.time.Instant;
import java.util.Map;

import com.ponomarenko.mugloar.domain.GameState;

/**
 * One action taken in a game and its outcome.
 *
 * @param turn turn number reported by the API after the action; counted locally for an investigation,
 * whose answer carries no turn
 * @param action what was done
 * @param targetId the ad or item id acted on
 * @param description the ad text or item name
 * @param family the ad's family (see {@link com.ponomarenko.mugloar.domain.Ad#family()}); empty for
 * purchases, investigations and ads whose text is unknown
 * @param success whether the ad was solved / the purchase went through; always true for an investigation
 * @param message explanation returned by the API (solve), or the reputation found (investigation)
 * @param reason why the strategy chose this action; empty for manual actions
 * @param automated whether the action was chosen by the autoplayer
 * @param stateAfter game state after the action
 * @param purchasesAfter what had been bought, and how often, once this action was done; filled in by
 * the session when the turn is recorded
 * @param at when the action completed
 */
public record TurnRecord(int turn, ActionType action, String targetId, String description, String family,
		boolean success, String message, String reason, boolean automated, GameState stateAfter,
		Map<String, Integer> purchasesAfter, Instant at) {

	/** The same turn with the purchases as they stood once it was recorded. */
	TurnRecord withPurchases(Map<String, Integer> purchases) {
		return new TurnRecord(turn, action, targetId, description, family, success, message, reason, automated,
				stateAfter, purchases, at);
	}
}
