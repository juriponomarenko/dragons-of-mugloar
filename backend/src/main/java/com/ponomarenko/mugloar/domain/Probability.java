package com.ponomarenko.mugloar.domain;

import java.util.Arrays;
import java.util.Locale;

/**
 * The textual probability labels the game attaches to ads, mapped to an estimated success
 * rate. The rates are empirical: the API never exposes numbers, so these were calibrated by
 * playing many games and are deliberately slightly conservative.
 */
public enum Probability {

	SURE_THING("Sure thing", 1.0),
	PIECE_OF_CAKE("Piece of cake", 0.95),
	WALK_IN_THE_PARK("Walk in the park", 0.9),
	QUITE_LIKELY("Quite likely", 0.8),
	HMMM("Hmmm....", 0.6),
	GAMBLE("Gamble", 0.5),
	RISKY("Risky", 0.4),
	RATHER_DETRIMENTAL("Rather detrimental", 0.3),
	PLAYING_WITH_FIRE("Playing with fire", 0.2),
	SUICIDE_MISSION("Suicide mission", 0.1),
	IMPOSSIBLE("Impossible", 0.0),
	/** A label this code has never seen; treated as fairly unsafe. */
	UNKNOWN("Unknown", 0.3);

	private final String label;

	private final double successRate;

	Probability(String label, double successRate) {
		this.label = label;
		this.successRate = successRate;
	}

	public String label() {
		return label;
	}

	public double successRate() {
		return successRate;
	}

	/**
	 * Resolves a label as sent by the API. Matching ignores case, punctuation and whitespace,
	 * so {@code "Hmmm...."}, {@code "hmmm"} and {@code "HMMM..."} all resolve to {@link #HMMM}.
	 */
	public static Probability fromLabel(String label) {
		if (label == null) {
			return UNKNOWN;
		}
		String key = normalize(label);
		return Arrays.stream(values())
				.filter(p -> p != UNKNOWN && normalize(p.label).equals(key))
				.findFirst()
				.orElse(UNKNOWN);
	}

	/** Whether the label is one the game is known to use. */
	public static boolean isKnownLabel(String label) {
		return fromLabel(label) != UNKNOWN;
	}

	private static String normalize(String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
	}
}
