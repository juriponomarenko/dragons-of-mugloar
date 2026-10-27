package com.ponomarenko.mugloar.config;

import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Tunable knobs of the game-playing strategy. All values have sensible defaults so the
 * application runs without any configuration; they can be overridden in
 * {@code application.properties} or via environment variables for experimentation.
 *
 * @param potionLivesThreshold buy a healing potion when lives are at or below this value
 * @param goldReserve gold to keep aside (for a potion) before buying upgrades
 * @param upgradeOrder shop item ids to buy, in order, when gold allows
 * @param rebuyUpgrades whether to keep buying upgrades after each one has been bought once
 * @param minSuccessRateCritical minimum success rate of an ad when only one life is left
 * @param minSuccessRateLow minimum success rate of an ad when two lives are left
 * @param minSuccessRateNormal minimum success rate of an ad otherwise
 * @param lifeValue gold-equivalent cost of losing a life, used when scoring ads
 * @param riskyKeywords words in an ad text that make it less attractive (reputation risk)
 * @param riskyKeywordPenalty multiplier applied to the score of ads containing a risky keyword
 * @param maxTurns hard upper bound on turns played by the autoplayer, as a safety net
 */
@Validated
@ConfigurationProperties(prefix = "mugloar.strategy")
public record StrategyProperties(
		@Min(0) @DefaultValue("3") int potionLivesThreshold,
		@Min(0) @DefaultValue("100") int goldReserve,
		@DefaultValue({ "cs", "gas", "wax", "tricks", "wingpot", "ch", "rf", "iron", "mtrix", "wingpotmax" })
		List<String> upgradeOrder,
		@DefaultValue("true") boolean rebuyUpgrades,
		@DecimalMin("0") @DecimalMax("1") @DefaultValue("0.8") double minSuccessRateCritical,
		@DecimalMin("0") @DecimalMax("1") @DefaultValue("0.6") double minSuccessRateLow,
		@DecimalMin("0") @DecimalMax("1") @DefaultValue("0.5") double minSuccessRateNormal,
		@Min(0) @DefaultValue("50") int lifeValue,
		@DefaultValue({ "steal", "kidnap", "assassinate", "infiltrate", "kill" }) List<String> riskyKeywords,
		@DecimalMin("0") @DecimalMax("1") @DefaultValue("0.6") double riskyKeywordPenalty,
		@Min(1) @DefaultValue("5000") int maxTurns) {

	public static final String HEALING_POTION_ID = "hpot";

	/** Minimum acceptable success rate for the given number of remaining lives. */
	public double minSuccessRateFor(int lives) {
		if (lives <= 1) {
			return minSuccessRateCritical;
		}
		if (lives == 2) {
			return minSuccessRateLow;
		}
		return minSuccessRateNormal;
	}
}
